package com.sequenceiq.cloudbreak.service.cluster.flow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.domain.CbUser;

@Service
public class EmailMimeMessagePreparator {

    @Value("${cb.smtp.sender.from:}")
    private String msgFrom;

    public MimeMessagePreparator prepareMessage(final CbUser user, final String subject, final String body) {
        return prepareMessage(user.getUsername(), subject, body);
    }

    public MimeMessagePreparator prepareMessage(final String to, final String subject, final String body) {
        return mimeMessage -> {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
            message.setFrom(msgFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body, true);
        };
    }
}
