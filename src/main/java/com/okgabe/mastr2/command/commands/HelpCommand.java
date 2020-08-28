/*
 * Copyright 2020 Gabriel Keller
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 4.0 International License.
 * A copy of this license can be found at
 * https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.
 */

package com.okgabe.mastr2.command.commands;

import com.okgabe.mastr2.Mastr;
import com.okgabe.mastr2.command.CommandBase;
import com.okgabe.mastr2.command.CommandCategory;
import com.okgabe.mastr2.command.CommandEvent;
import com.okgabe.mastr2.event.ReactionListenerIdentity;
import com.okgabe.mastr2.util.BotRole;
import com.okgabe.mastr2.util.StringUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HelpCommand extends CommandBase {

    private List<MessageEmbed> helpPages = new ArrayList<>();
    private List<MessageEmbed> adminHelpPages = new ArrayList<>();
    private int maxDefaultPages;
    private static final MessageEmbed FRONT_PAGE = new EmbedBuilder()
            .setTitle("Mastr Help")
            .setDescription("You can utilize this message to navigate the different commands offered by Mastr.\nUse the reactions on the bottom to " +
                    "switch between command pages, or click the mailbox to send you a list of my commands to your inbox.")
            .build();

    public HelpCommand(Mastr mastr) {
        super(mastr);
        this.command = "help";
        this.aliases = new String[] {"h", "command", "cmd"};
        this.description = "Describes how to use Mastr and its commands";
        this.category = CommandCategory.MASTR;
        this.syntax = new String[] {"[page] - Browse the bot's commands", "<command> - Command-specific help"};
        this.examples = new String[] {"help 4", "help kick"};
    }

    @Override
    public boolean called(CommandEvent e) {
        return true;
    }

    @Override
    public void execute(CommandEvent e) {
        boolean isAdmin =  e.getBotUser().getRole().isAtOrAbove(BotRole.BOT_ADMINISTRATOR);

        if(e.getArgs().length == 0){
            sendHelpEmbed(e.getAuthor().getUser(), e.getChannel(), -1, isAdmin);
        }
        else{
            int page;
            try{
                page = Integer.parseInt(e.getArgs()[0]);

                if(page < 0 || isAdmin && page > adminHelpPages.size() || !isAdmin && page > maxDefaultPages){
                    e.getChannel().sendMessage("❌ That page doesn't exit!").queue();
                    return;
                }

                sendHelpEmbed(e.getAuthor().getUser(), e.getChannel(), page, isAdmin);
            }
            catch(NumberFormatException ex){
                CommandBase cmd = mastr.getCommandHandler().getCommand(e.getArgs()[0]);
                if(cmd == null){
                    e.getChannel().sendMessage("❌ That command doesn't exit!").queue();
                    return;
                }

                e.getChannel().sendMessage(createCommandHelpPage(cmd).build()).queue();
            }
        }
    }

    public EmbedBuilder createCommandHelpPage(CommandBase cmd){
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("`" + cmd.getCommand() + "`");
        eb.appendDescription(cmd.getDescription())
                .appendDescription("\n\n**Syntax**:\n")
                .appendDescription(StringUtil.join(cmd.getSyntax(), "\n"));

        if(cmd.getAliases().length>0){
            eb.appendDescription("\n**Alias" + (cmd.getAliases().length>1 ? "es" : "") + "**: " + StringUtil.join(cmd.getAliases(), ", "));
        }

        eb.appendDescription("\n**Category**: ").appendDescription(cmd.getCategory().getName());
        if(cmd.getMinimumRole().isAbove(BotRole.DEFAULT)){
            eb.appendDescription("\n**Permission**: ").appendDescription(cmd.getMinimumRole().getName());
        }

        return eb;
    }

    public void sendHelpEmbed(User owner, MessageChannel channel, int page, boolean admin){
        channel.sendMessage((page == -1 ? FRONT_PAGE : (admin ? adminHelpPages.get(page) : helpPages.get(page)))).queue(m -> {
            HelpReactionListener helpReactionListener = new HelpReactionListener(owner.getJDA(), channel.getType(), channel.getIdLong(), m.getIdLong(), owner.getIdLong(), 5*60,
                    reaction -> {
                HelpReactionListener listener = (HelpReactionListener) reaction;
                String name = listener.getReaction().getReactionEmote().getName();
                boolean updated = false;
                switch(name) {
                    case "⏪":
                        updated = listener.firstPage();
                        break;
                    case "◀":
                        updated = listener.decrementPage();
                        break;
                    case "▶":
                        updated = listener.incrementPage();
                        break;
                    case "⏩":
                        updated = listener.lastPage();
                        break;
                    case "\u23F9":
                        listener.retrieveMessage().queue(m2 -> m2.delete().queue());
                        break;
                    case "\uD83D\uDCE5":
                        listener.getChannel().sendMessage(listener.getUser().getAsMention() + ", check your inbox!").queue();
                        break;
                }
                if(updated)
                    listener.retrieveMessage().queue(m2 -> setPage(m2, listener.getPage(), listener.isAdmin()));

                listener.getReaction().removeReaction(listener.getUser()).queue();
                }, timeout -> {
                RestAction<Message> message = timeout.retrieveMessage();
                if(message!=null) message.queue(mes -> m.clearReactions().queue());
            }, page, admin);

            mastr.getReactionHandler().register(helpReactionListener);
            // Set failure hook to nothing in-case someone deletes the message before reactions are done
            m.addReaction("⏪").queue(ignoredm -> {}, f -> {});
            m.addReaction("◀").queue(ignoredm -> {}, f -> {});
            m.addReaction("▶").queue(ignoredm -> {}, f -> {});
            m.addReaction("⏩").queue(ignoredm -> {}, f -> {});
            m.addReaction("\u23F9").queue(ignoredm -> {}, f -> {});
            m.addReaction("\uD83D\uDCE5").queue(ignoredm -> {}, f -> {});
        });
    }

    public void setPage(Message m, int page, boolean admin){
        m.editMessage((admin ? adminHelpPages.get(page) : helpPages.get(page))).queue();
    }

    public void buildCommandPages(){
        // Get page limits (for admins and not for admins)
        List<CommandBase> commands = mastr.getCommandHandler().getCommands();
        Map<CommandCategory, List<CommandBase>> organizedCommands = new HashMap<>();
        int maxAllPages = 0;

        // Sort commands and increment page count
        // It's necessary I do this first THEN make page embeds so I can get the total number of pages (not including pages only for admins) and ACTUAL total
        List<CommandCategory> categories = new ArrayList<>(Arrays.asList(CommandCategory.values()));
        categories.sort(Comparator.comparingInt(CommandCategory::getId));

        for(int i = 0; i < categories.size(); i++){
            CommandCategory category = categories.get(i);
            List<CommandBase> cmds = commands.stream()
                    .filter(cmd -> cmd.getCategory() == category)
                    .collect(Collectors.toList());
            logger.debug(category.toString());
            logger.debug(cmds.size() + "");
            if(cmds.size()==0) {
                categories.remove(category);
                i = i-1;
                continue;
            }
            organizedCommands.put(category, cmds);

            if(category == CommandCategory.MASTR_ADMIN){
                maxDefaultPages = maxAllPages;
            }
            maxAllPages += (cmds.size() / 5)+1;
        }

        int currentPage = -1;
        // For each category,
        for(CommandCategory cat : categories){
            List<CommandBase> cmdsInCat = organizedCommands.get(cat);
            // Get each page in that category,
            for(int x = 0; x < (cmdsInCat.size()/5) + 1; x++){
                currentPage++;
                EmbedBuilder pageBuilder = new EmbedBuilder();
                pageBuilder.setTitle("Mastr Help (Page " + (currentPage+1) + "/" + maxDefaultPages + ")");
                pageBuilder.setDescription("Use `help <command>` for command details");

                pageBuilder.addField(cat.getName(), cat.getDescription(), false);

                // And loop for each command in that page.
                for(int cmdSelector = x*5; cmdSelector < Math.min(x*5+5, cmdsInCat.size()); cmdSelector++){
                    CommandBase cmd = cmdsInCat.get(cmdSelector);
                    pageBuilder.addField(cmd.getCommand(), cmd.getDescription(), false);
                }

                // Then store the page
                helpPages.add(pageBuilder.build());

                pageBuilder.setTitle("Mastr Help (Page " + (currentPage+1) + "/" + maxAllPages + ")");
                adminHelpPages.add(pageBuilder.build());
            }
        }
    }

    private class HelpReactionListener extends ReactionListenerIdentity {

        private int page;
        private boolean admin;
        public HelpReactionListener(JDA jda, ChannelType channelType, long channelId, long messageId, long userId, long timeout,
                                    Consumer<ReactionListenerIdentity> handler, Consumer<ReactionListenerIdentity> timeoutHandler, int page, boolean admin) {
            super(jda, channelType, channelId, messageId, userId, timeout, handler, timeoutHandler);
            this.page = page;
            this.admin = admin;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public boolean incrementPage(){
            if(admin){
                if(page == adminHelpPages.size()-1) return false;
            }
            else{
                if(page == maxDefaultPages) return false;
            }

            page++;
            return true;
        }

        public boolean decrementPage(){
            if(page == 0) return false;
            page--;
            return true;
        }

        public boolean isAdmin() {
            return admin;
        }

        public boolean firstPage(){
            if(page==0) return false;
            page = 0;
            return true;
        }

        public boolean lastPage(){
            int lastPage = (admin ? adminHelpPages.size()-1 : maxDefaultPages);
            if(page == lastPage) return false;

            page = lastPage;
            return true;
        }
    }
}
