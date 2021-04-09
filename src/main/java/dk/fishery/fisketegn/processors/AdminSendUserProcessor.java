package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class AdminSendUserProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        BasicDBObject user = (BasicDBObject) exchange.getProperty("newUser");
        user.removeField("password");
        user.removeField("_id");
        exchange.getIn().setBody(user);
    }
}
