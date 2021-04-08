package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;

import java.util.UUID;

public class createLicenseProcessor implements org.apache.camel.Processor {
    private static int licesenceNumberCounter;
    @Override
    public void process(Exchange exchange) throws Exception {
        User input =(User) exchange.getProperty("oldBody");
        licesenceNumberCounter =   (int) exchange.getProperty("licenseNumber");
        License license = new License();
        license.setLicenseID(UUID.randomUUID().toString());
        license.setLicenseNumber(String.valueOf(licesenceNumberCounter));
        license.setType(input.getType());
        license.setStatus(true);
        license.setStartDate(input.getStartDate());
        license.setOriginalStartDate(input.getStartDate());
        license.setHighQuality(input.isHighQuality());
        exchange.setProperty("licenseID", license.getLicenseID());
        exchange.setProperty("licenseNumber", licesenceNumberCounter);
        exchange.getIn().setBody(license);
        licesenceNumberCounter++;

    }
}
