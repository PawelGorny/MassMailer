import com.sun.mail.imap.protocol.FLAGS;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

public class EmailService {

    private Session session;
    private String from;
    private String subject;
    private String body;
    private File file;

    private String host;
    private String portSmtp;
    private String portImap;
    private char[] password;

    public EmailService(String host, String from, char[] _password, String subject) {
        this.from = from;
        this.subject = subject;
        Properties props = new Properties();
        String[] hostData = host.split(":");
        this.host = hostData[0];
        this.portSmtp = hostData[1];
        this.portImap = hostData[2];
        this.password = _password;
        props.put("mail.smtp.host", this.host);
        props.put("mail.smtp.ssl.enable", true);
        props.put("mail.smtp.port", this.portSmtp);
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.imap.host", this.host);
        props.put("mail.imap.ssl.enable", true);
        props.put("mail.imap.port", this.portImap);
        props.put("mail.imap.auth", true);
        props.put("mail.imap.ssl.trust", "*");
        Authenticator authenticator = new Authenticator() {
            private PasswordAuthentication pa = new PasswordAuthentication(from, new String(password));

            @Override
            public PasswordAuthentication getPasswordAuthentication() {
                return pa;
            }
        };
        session = Session.getInstance(props, authenticator);
        session.setDebug(true);
    }

    public String test() {
        try {
            session.getTransport().connect();
            return null;
        } catch (MessagingException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    public void setBody(String body) {
        this.body = body.replaceAll("\n", "<br/>");
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void send(String to, String[] bcc) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setSubject(subject);
        message.setSentDate(new Date());
        if (to != null && !to.isEmpty()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        }
        for (String toBcc : bcc) {
            if (toBcc != null && !toBcc.isEmpty()) {
                message.addRecipient(Message.RecipientType.BCC, new InternetAddress(toBcc));
            }
        }
        message.addRecipient(Message.RecipientType.CC, new InternetAddress(from));
        message.setFrom(new InternetAddress(from));

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(body, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        if (file != null) {
            MimeBodyPart attachPart = new MimeBodyPart();
            try {
                attachPart.attachFile(file);
                multipart.addBodyPart(attachPart);
            } catch (IOException ex) {
                throw new MessagingException(ex.getMessage(), ex);
            }
        }

        message.setContent(multipart);
        Transport.send(message);
        try {
            Thread.sleep(1000);
            copyToSent(message);
            Thread.sleep(1000);
        } catch (Exception e) {

        }
    }


    private void copyToSent(Message message) throws MessagingException {
        Store store = null;
        try {
            store = session.getStore("imap");
            store.connect(this.host, this.from, String.valueOf(this.password));
            Folder folder = store.getFolder("Sent");
            folder.open(Folder.READ_WRITE);
            folder.appendMessages(new Message[]{message});
            message.setFlag(FLAGS.Flag.RECENT, true);
            message.setFlag(FLAGS.Flag.SEEN, true);
        } catch (Exception ignore) {
            System.out.println("error processing message " + ignore.getMessage());
        } finally {
            if (store != null) {
                store.close();
            }
        }
    }
}
