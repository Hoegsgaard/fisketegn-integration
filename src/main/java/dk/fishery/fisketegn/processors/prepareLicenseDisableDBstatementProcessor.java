package dk.fishery.fisketegn.processors;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.camel.Exchange;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.bson.conversions.Bson;

import java.util.ArrayList;

public class prepareLicenseDisableDBstatementProcessor implements org.apache.camel.Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        ArrayList<String> licenseIDs = (ArrayList<String>) exchange.getProperty("licenseIDs");
        String flagToChange = (String) exchange.getProperty("flagToChange");
        Boolean flagValue = (Boolean) exchange.getProperty("flagValue");
        ArrayList<Bson> criteriaList = new ArrayList<>();
        for(String licenseID: licenseIDs){
            Bson criteria = Filters.eq("licenseID", licenseID);
            criteriaList.add(criteria);
        }
        Bson criteria = Filters.or(criteriaList);
        Bson updateObj = Updates.set(flagToChange, flagValue);
        exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        exchange.getIn().setHeader(MongoDbConstants.MULTIUPDATE, true);
        exchange.getIn().setBody(updateObj);
    }
}
