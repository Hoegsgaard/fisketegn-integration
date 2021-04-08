package dk.fishery.fisketegn.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class sendEmailProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    String to = "jupperify@gmail.com";
    String from = "fisketegndtu@gmail.com";
    String password = (String) exchange.getProperty("emailPass");

    Properties prop = new Properties();
    prop.put("mail.smtp.host", "smtp.gmail.com");
    prop.put("mail.smtp.post", "465");
    prop.put("mail.smtp.auth", "true");
    prop.put("mail.smtp.socketFactory.port", "465");
    prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    Session session = Session.getInstance(prop, new javax.mail.Authenticator(){
      protected PasswordAuthentication getPasswordAuthentication(){
        return new PasswordAuthentication(from, password);
      }
    });

    try {
      MimeMessage message = new MimeMessage(session);

      message.setFrom(new InternetAddress(from));
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
      message.setSubject("Tillykke med dit fisketegn");
      message.setText("Dit fisketegnsnummer er #123");

      Transport.send(message);
      System.out.println("Sent message successfully....");
    }catch (MessagingException e){
      e.printStackTrace();
    }
  }
}
