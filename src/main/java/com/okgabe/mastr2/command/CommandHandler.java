/*
 * Copyright 2020 Gabriel Keller
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 4.0 International License.
 * A copy of this license can be found at
 * https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.
 */

package com.okgabe.mastr2.command;

import com.okgabe.mastr2.Mastr;
import com.okgabe.mastr2.entity.BotGuild;
import com.okgabe.mastr2.entity.BotUser;
import com.okgabe.mastr2.util.ReflectionUtil;
import com.okgabe.mastr2.util.StringUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CommandHandler {

    private static Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    private Mastr mastr;
    private String mastrId;
    private List<CommandBase> commands;


    /**
     * Initializes the CommandHandler and registers all commands located in the commands package
     *
     * @param mastr Bot instance
     */
    public CommandHandler(Mastr mastr) {
        this.mastr = mastr;
        mastrId = mastr.getShardManager().getShardById(0).getSelfUser().getId();
        commands = new ArrayList<>();

        logger.info("Registering commands...");
        try{
            for(Class c : ReflectionUtil.getClasses("com.okgabe.mastr2.command.commands")){
                commands.add((CommandBase)c.getConstructors()[0].newInstance(mastr));
            }
        }
        catch(Exception ex){
            logger.error("Failed to load commands:");
            ex.printStackTrace();
        }
        logger.info("Commands registered");
        logger.info("Scanning for overlapping command aliases...");

        // Scan through commands to see if there are any duplicate aliases
        List<String> commandAliases = new ArrayList<>();
        List<String> duplicateCommands = new ArrayList<>();

        for(CommandBase cmd : commands){
            for(int i = -1; i < cmd.getAliases().length; i++){
                // Loop through command and all aliases in command
                String alias = (i == -1 ? cmd.getCommand() : cmd.getAliases()[i]);

                if(commandAliases.contains(alias)){
                    duplicateCommands.add(alias);
                }
                else commandAliases.add(alias);
            }
        }

        if(duplicateCommands.size() > 0){
            logger.error("There are duplicate command aliases!");
            logger.error(StringUtil.join(duplicateCommands.toArray(new String[duplicateCommands.size()])), ", ");
        }
        else logger.info("Overlap check complete");
    }

    /**
     * Initial handling of incoming bot messages.
     * Looks for the guild prefix, handles pinging the bot to check the prefix, and passes on execution of commands to
     * parseCommand()
     *
     * @param e Message event
     */
    public void handleMessage(MessageReceivedEvent e){
        String content = e.getMessage().getContentRaw();
        String prefix = retrievePrefix(e.getGuild().getIdLong());

        if(content.startsWith(prefix)){
            parseCommand(e.getMember(), e.getTextChannel(), e.getMessage(), prefix);
        }
        else if(content.startsWith("<@" + mastrId + ">")){
            if(content.trim().length() <= 21){
                e.getChannel().sendMessage("Hey! My prefix for this server is `" + prefix + "`. If you need help, you can use `" + prefix + "help` or DM me!" +
                        "\nIf you don't like remembering bot prefixes, mentioning me works as a command prefix as well.").queue();
            }
            else parseCommand(e.getMember(), e.getTextChannel(), e.getMessage(), "<@" + mastrId + "> ");
        }
        else if(content.startsWith("<@!" + mastrId + ">")){
            if(content.trim().length() <= 22){
                e.getChannel().sendMessage("Hey! My prefix for this server is `" + prefix + "`. If you need help, you can use `" + prefix + "help` or DM me!" +
                        "\nIf you don't like remembering bot prefixes, mentioning me works as a command prefix as well.").queue();
            }
            else parseCommand(e.getMember(), e.getTextChannel(), e.getMessage(), "<@!" + mastrId + "> ");
        }
    }

    /**
     * Handles parsing of incoming commands triggered with the Mastr alias
     *
     * @param author Executor of the command
     * @param channel Channel in which the command was executed in
     * @param message Message containing the command
     * @param prefix Prefix used to execute the command
     */
    public void parseCommand(Member author, MessageChannel channel, Message message, String prefix){
        // Get information about the command
        String content = message.getContentRaw();
        String contentNoPrefix = content.substring(prefix.length());
        String[] argsWithCommand = contentNoPrefix.split(" ");
        CommandBase cmd = searchForCommand(argsWithCommand[0]);
        if(cmd == null) return;

        // Get args and execute command
        String[] args = new String[argsWithCommand.length - 1];

        for(int i = 1; i < args.length+1; i++){
            args[i-1] = argsWithCommand[i];
        }

        executeCommand(cmd, author, channel, message, args);
    }

    public void executeCommand(CommandBase cmd, Member author, MessageChannel channel, Message message, String[] args)  {
        logger.debug("Command " + cmd.getCommand() + " received from user " + author.getUser().getName() + " (" + author.getUser().getId() + ") in guild " + author.getGuild().getName() + " (" + author.getGuild().getId() + ")");
        try{
            BotUser user = mastr.getDatabaseManager().getBotUser(author.getIdLong());
            BotGuild guild = mastr.getDatabaseManager().getBotGuild(author.getGuild().getIdLong());

            if(mastr.getPermissionManager().isBannedUser(user)) return;
            if(mastr.getPermissionManager().isBannedGuild(guild)) return;

            if(!user.getRole().isAtOrAbove(cmd.getMinimumRole())){
                channel.sendMessage("❌ You must be a `" + cmd.getMinimumRole().getName() + "` or above to run this command.").queue();
                return;
            }

            boolean properCall = cmd.called(args);
            if(properCall){
                user.incrementTimesUsed();
                user.set(mastr.getDatabaseManager());
                guild.incrementTimesUsed();
                guild.set(mastr.getDatabaseManager());

                cmd.execute(author, channel, message, args);
            }
            else{
                channel.sendMessage("❌ Wrong command usage.").queue();
            }
        }
        catch(InsufficientPermissionException ex){
            channel.sendMessage("❌ I need the permission: " + ex.getPermission().getName()).queue();
        }
        catch(Exception ex){
            channel.sendMessage("❌ An error occurred.").queue();
            logger.debug("An error has occurred running " + cmd.getCommand() + ": ", ex);
        }
    }

    /**
     * Searches for the command given an alias
     *
     * @param command Command alias used
     * @return CommandBase from the search (null if not found)
     */
    public CommandBase searchForCommand(String command){
        command = command.toLowerCase();
        for(CommandBase cmd : commands){
            for(int i = -1; i < cmd.getAliases().length; i++){
                String alias = (i == -1 ? cmd.getCommand() : cmd.getAliases()[i]);
                if(command.equals(alias)) return cmd;
            }
        }

        return null;
    }



    /**
     * Attempt to get the cached prefix for a guild, or if not cached, get from the database
     *
     * @param guildId ID of the guild to search for
     * @return Prefix for the guild (null if invalid guild)
     */
    public String retrievePrefix(long guildId){
        String cached = mastr.getCacheManager().getPrefix(guildId);

        if(cached!=null) return cached;

        String prefix = mastr.getDatabaseManager().getGuildPrefix(guildId);
        mastr.getCacheManager().setPrefix(guildId, prefix);
        return prefix;
    }
}
