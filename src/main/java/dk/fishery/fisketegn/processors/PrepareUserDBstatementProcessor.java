package dk.fishery.fisketegn.processors;

import com.mongodb.client.model.Filters;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.bson.conversions.Bson;

public class PrepareUserDBstatementProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        String email;
        if(exchange.getProperty("userRole") != null && exchange.getProperty("userRole").equals("admin")){
            email = (String) exchange.getProperty("usersEmail");
        }else{
            email = (String) exchange.getProperty("userEmail");
        }
        Bson criteria = Filters.eq("email", email);
        exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
    }
}
