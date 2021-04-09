package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mindrot.jbcrypt.BCrypt;

public class CheckPasswordProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject DbUser = exchange.getIn().getBody(BasicDBObject.class);
    User user = (User) exchange.getProperty("oldBody");
    boolean passwordIsCorrect = BCrypt.checkpw(user.getPassword(), DbUser.getString("password"));
    exchange.setProperty("userAuth", passwordIsCorrect);
    exchange.getIn().setBody(DbUser);
  }
}
