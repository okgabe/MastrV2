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
import com.okgabe.mastr2.entity.BotGuild;
import com.okgabe.mastr2.entity.BotUser;
import com.okgabe.mastr2.util.StringUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

public class PrefixCommand extends CommandBase {

    private static final char[] WHITELISTED_CHARACTERS = "qwertyuiopasdfghjklzxcvbnm,./<>?1234567890!@#$%^&*()-=_+[{;:'\"".toCharArray();

    public PrefixCommand(Mastr mastr) {
        super(mastr);
    }

    @Override
    public boolean called(String[] args) {
        return true;
    }

    @Override
    public void execute(Member author, BotGuild guild, BotUser user, MessageChannel channel, Message message, String[] args) {
        if(args.length == 0){
            channel.sendMessage("This server's prefix is `" + guild.getPrefix() + "`").queue();
            return;
        }
        else if(args.length>2){
            channel.sendMessage("❌ Your prefix must not have more than one space").queue();
            return;
        }

        String prefix = StringUtil.join(args);

        if(guild.getPrefix().equals(prefix)){
            channel.sendMessage("❌ This server's prefix is already `" + prefix + "`").queue();
            return;
        }

        if(prefix.length()>20){
            channel.sendMessage("❌ Your prefix cannot be longer than 20 characters").queue();
            return;
        }

        for(char prefixChar : prefix.toCharArray()){
            boolean isWhitelistedChar = false;
            for(char allowedChar : prefix.toCharArray()){
                if(prefixChar == allowedChar) {
                    isWhitelistedChar = true;
                    break;
                }
            }

            if(!isWhitelistedChar){
                channel.sendMessage("❌ That message contains a blacklisted character: " + prefixChar).queue();
                return;
            }
        }

        guild.setPrefix(prefix);
        guild.set(mastr.getDatabaseManager());
        mastr.getCacheManager().setPrefix(guild.getGuildId(), prefix);
        channel.sendMessage("✅ This server's prefix has been changed to `" + prefix + "`").queue();
    }

    @Override
    public String getCommand() {
        return "prefix";
    }

    @Override
    public String getDescription() {
        return "Change the bot's prefix for this server";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MASTR;
    }

    @Override
    public String[] getSyntax() {
        return new String[] {"<prefix>"};
    }
}
