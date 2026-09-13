package com.omnicare.platform.conversation.application;

import org.springframework.stereotype.Component;

/**
 * The stand-in reply until Day 7 wires in a real model.
 *
 * <p>Deliberately says what it is. A placeholder that sounds like a real answer
 * is one that survives into a demo unnoticed.
 */
@Component
class PlaceholderReplier implements Replier {

    private static final String REPLY =
            "Thanks for your message. I am not connected to a language model yet, "
                    + "so I cannot answer properly — a human will pick this up.";

    @Override
    public String replyTo(String visitorMessage) {
        return REPLY;
    }
}
