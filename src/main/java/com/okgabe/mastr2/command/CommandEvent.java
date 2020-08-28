/*
 * Copyright 2020 Gabriel Keller
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 4.0 International License.
 * A copy of this license can be found at
 * https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.
 */

package com.okgabe.mastr2.command;

import com.okgabe.mastr2.entity.BotGuild;
import com.okgabe.mastr2.entity.BotUser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

public class CommandEvent {
    private Member author;
    private BotGuild botGuild;
    private BotUser botUser;
    private MessageChannel channel;
    private Message message;
    private String[] args;

    public CommandEvent(Member author, BotGuild botGuild, BotUser botUser, MessageChannel channel, Message message, String[] args) {
        this.author = author;
        this.botGuild = botGuild;
        this.botUser = botUser;
        this.channel = channel;
        this.message = message;
        this.args = args;
    }

    public Member getAuthor() {
        return author;
    }

    public void setAuthor(Member author) {
        this.author = author;
    }

    public BotGuild getBotGuild() {
        return botGuild;
    }

    public void setBotGuild(BotGuild botGuild) {
        this.botGuild = botGuild;
    }

    public BotUser getBotUser() {
        return botUser;
    }

    public void setBotUser(BotUser botUser) {
        this.botUser = botUser;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public JDA getJDA(){
        return author.getJDA();
    }
}