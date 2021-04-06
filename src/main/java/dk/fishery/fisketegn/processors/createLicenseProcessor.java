package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;

import java.util.UUID;

public class createLicenseProcessor implements org.apache.camel.Processor {
    private static int licesenceNumberCounter = 0;
    @Override
    public void process(Exchange exchange) throws Exception {
        User input =(User) exchange.getProperty("oldBody");
        License license = new License();
        license.setLicenseID(UUID.randomUUID().toString());
        license.setLicenseNumber(String.valueOf(licesenceNumberCounter));
        licesenceNumberCounter++;
        license.setType(input.getType());
        license.setStatus(true);
        license.setStartDate(input.getStartDate());
        license.setOriginalStartDate(input.getStartDate());
        license.setHighQuality(input.isHighQuality());

        exchange.setProperty("licenseID", license.getLicenseID());
        exchange.getIn().setBody(license);
    }
}
