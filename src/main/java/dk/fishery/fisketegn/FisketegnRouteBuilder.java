package dk.fishery.fisketegn;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.User;
import dk.fishery.fisketegn.processors.*;
import org.apache.camel.*;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.util.json.JsonObject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.apache.camel.component.mongodb.MongoDbConstants.RESULT_PAGE_SIZE;

@Component
public class FisketegnRouteBuilder extends RouteBuilder {

  @Value("${fisketegn.api.path}")
  String contextPath;

  @Value("${server.port}")
  String serverPort;

  @Value("${jwtSecure.key}")
  String jwtKey;

  @Value("${email.password}")
  String pass;

  @Value("${licenseNumber.number}")
  int licenseNumber;


    @Override
    public void configure() {
      //CamelContext context = new DefaultCamelContext();

      onException(IOException.class)
              .log("IOException")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("nothing found in query"));
      onException(RuntimeCamelException.class)
              .log("Camel exception")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("camel exception"));


      restConfiguration()
      .contextPath(contextPath)
      .port(serverPort)
      .enableCORS(true)
      .apiContextPath("/api-doc")
      .apiProperty("api.title", "rest api")
      .apiProperty("api.version", "v1")
      .apiProperty("cors", "true")
      .apiContextRouteId("doc-api")
      .component("servlet")
      .bindingMode(RestBindingMode.json)
      .dataFormatProperty("prettyPrint", "true");




      rest("/api/").description("dk.fishery.SpringBootStarter Rest")
      .id("api-route")
      .produces(MediaType.APPLICATION_JSON)
      .consumes(MediaType.APPLICATION_JSON)
      .bindingMode(RestBindingMode.json);

      // AUTH
      rest("/api/auth/")

      .post("/login")
      .type(User.class)
      .to("direct:doesUserExist")

      .post("/validateToken")
      .to("direct:validateToken");

      rest("/api/license")

      .post("/")
      .type(User.class)
      .to("direct:findOrCreateUser")

      .put("/")
      .to("direct:updateLicense");

      // TEST
      rest("api/test")
      .post("/email")
      .to("direct:sendEmail");

      // USER
       rest("/api/user/")

      .put("/")
      .type(User.class)
      .to("direct:updateUser")

      .get("/")
      .to("direct:getUser")

      .delete("/")
      .to("direct:deleteUser")

      .put("/updatePassword")
      .to("direct:updatePassword")

      .get("license")
      .to("direct:getLicense");

      // Admin
      rest("/api/admin/")

      .get("/User")
      .to("direct:adminGetUser")

      .put("/User")
      .type(User.class)
      .to("direct:adminUpdateUser")

      .put("User/role")
      .to("direct:adminUpdateUserRole")

      .put("refund")
      .to("direct:flagLicense");


      // TEST
      from("direct:sendEmail")
      .setProperty("emailPass", constant(pass))
      .process(new SendEmailProcessor());

      // Auth Endpoints
      from("direct:validateToken")
      .streamCaching()
      .setProperty("tokenKey", constant(jwtKey))
      .process(new ValidateTokenProcessor())
      .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
        .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
      ;

      //Used for logging in users
      from("direct:doesUserExist")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      .setProperty("userEmail", simple("${body.email}"))
      .process(new PrepareUserDBstatementProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .setProperty("newBody", simple("${body}"))
      //Check if user exists
      .choice()
        .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
          .log("Bruger eksisterer")
          .process(new CheckPasswordProcessor())
          //check if login information is correct
          .choice()
            .when(exchangeProperty("userAuth").isEqualTo(true))
              .process(new GenerateTokenProcessor())
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
          .endChoice()
        .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      // FIND OR CREATE USER
      //maybe use doesUserExist
      from("direct:findOrCreateUser")
      .streamCaching()
      .routeId("findOrCreateUser")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      .setProperty("userEmail", simple("${body.email}"))
      .process(new PrepareUserDBstatementProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .choice()
      .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
      .setProperty("newUser", simple("${body}"))
      //Bruger eksisterer
      .process(new CheckPasswordProcessor())
      .choice()
        .when(exchangeProperty("userAuth").isEqualTo(true))
      // .to("direct:payment")
      .process(new GenerateTokenProcessor())
      .to("direct:createLicense")
      .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
      .endChoice()
      .otherwise()
      //.log("Bruger eksisterer endnu ikke")
      .to("direct:createUser");

      from("direct:createUser")
      //.to("direct:payment")
      .process(new HashPasswordProcessor())
      // Gem bruger i DB
      .process(exchange -> {
        User user = (User) exchange.getIn().getBody();
        user.setRole("user");
        BasicDBObject dbUser = user.getDbObject();
        exchange.getIn().setBody(dbUser);
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert")
      .process(new GenerateTokenProcessor())
      .to("direct:createLicense");

      //Get licensenumber from database.
      from("direct:getMaxLicenseNumber")
      .process(exchange -> {
                Bson criteria = Filters.exists("licenseNumber");
                exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
              })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
      .process(exchange -> {
        ArrayList<Document> licenses = (ArrayList<Document>) exchange.getIn().getBody();
        int maxNumber = 54321;
        for(Document l: licenses){
          if (Integer.parseInt(l.getString("licenseNumber")) > maxNumber){
            maxNumber = Integer.parseInt(l.getString("licenseNumber"));
          }
        }
        maxNumber++;
        licenseNumber = maxNumber;
        exchange.setProperty("licenseNumber", licenseNumber);
      });

      //Create a license
      from("direct:createLicense")
      //Check whether or not the system already knows the most recent licenseNumber
      //if not, retrieve from database
      .choice()
          .when(constant(licenseNumber).isEqualTo(-1))
          .to("direct:getMaxLicenseNumber")
          .end()
      //Create license object and insert into database
      .process(new CreateLicenseProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=insert")
      //grab user from database
      .process(new PrepareUserDBstatementProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      //Add licenseID to list of users licenses and reinsert into database
      .process(exchange -> {
        BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
        ArrayList<String> licenses = (ArrayList<String>) user.get("licenses");
        if(licenses == null){
          licenses = new ArrayList<>();
        }
        licenses.add((String) exchange.getProperty("licenseID"));
        user.put("licenses", licenses);
        exchange.getIn().setBody(user);
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
      .setProperty("emailPass", constant(pass))
      //send receipt by email
      .process(new SendEmailProcessor())
      .process(new Processor() {
          @Override
          public void process(Exchange exchange) throws Exception {
              JsonObject json = new JsonObject();
              json.put("license", exchange.getProperty("license"));
              json.put("token", exchange.getProperty("userToken"));
              exchange.getIn().setBody(json);
          }
      })
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

      //Update date on an existing license
      from("direct:updateLicense")
              //validate user
              .setProperty("tokenKey", constant(jwtKey))
              .setProperty("licenseID", simple("${body}"))
              .process(new ValidateTokenProcessor())
              .choice()
                .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
                //get user info
                .process(new PrepareUserDBstatementProcessor())
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
                //get list of users licenses
                .process(exchange -> {
                  ArrayList listOfUsers = exchange.getIn().getBody(ArrayList.class);
                  exchange.setProperty("user", listOfUsers.get(0));
                  LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
                  String licenseID = prop.get("licenseID");
                  Bson criteria = Filters.eq("licenseID", licenseID);
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
                //extract license and update date
                .process(new UpdateLicenseProcessor())
                //insert back into db
                .process(exchange -> {
                  LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
                  String licenseID = prop.get("licenseID");
                  Bson criteria = Filters.eq("licenseID", licenseID);
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=save")
                .setProperty("emailPass", constant(pass))
                .process(new SendEmailProcessor())
                .setBody(exchangeProperty("license"))
              .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      // User endpoints
      // UPDATE USER
      from("direct:updateUser")
      //validate user
      .setProperty("tokenKey", constant(jwtKey))
      .setProperty("user", simple("${body}"))
      .process(new ValidateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          //Get old user from database
          .process(exchange -> exchange.getIn().setBody(exchange.getProperty("user")))
          .process(new PrepareUserDBstatementProcessor())
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          //update info on user object and reinsert into database
          .process(new UpdateUserProcessor())
          .setProperty("newUser", simple("${body}"))
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
          .setBody(exchangeProperty("newUser"))
          .process(new GenerateTokenProcessor())
      .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      // GET USER
      from("direct:getUser")
      //validate user
      .setProperty("tokenKey", constant(jwtKey))
      .process( new ValidateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          //get user from database and trim fields the user shouldn't see.
          .process(new PrepareUserDBstatementProcessor())
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .process(exchange -> {
            BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
            user.removeField("password");
            user.removeField("role");
            user.removeField("_id");
            exchange.getIn().setBody(user);
          })
          .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

        // DELETE USER
        from("direct:deleteUser")
        //validate user
        .setProperty("tokenKey", constant(jwtKey))
        .process(new ValidateTokenProcessor())
        .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
            .process(new PrepareUserDBstatementProcessor())
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
            .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                .process(exchange -> {
                BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
                exchange.setProperty("licenseIDs", user.get("licenses"));
                exchange.setProperty("user",user);
                exchange.getIn().setBody(user);
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=remove")
                .to("direct:disableLicenses")
                .setBody(simple("User deleted"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .endChoice()
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      from("direct:disableLicenses")
      .setBody(exchangeProperty("user"))
      .process(new GetLicenseProcessor())
      .process(exchange -> {
          ArrayList<Bson> bsons = (ArrayList<Bson>) exchange.getProperty("bsons");
          Bson criteria = Filters.or(bsons);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
      .choice()
      .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
      //trim fields from licenses the user shouldn't see.
      .setProperty("flagToChange", constant("deletedFlag"))
      .setProperty("flagValue", constant(true))
      .process(new prepareLicenseDisableDBstatementProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=update");

      // UPDATE PASSWORD
      from("direct:updatePassword")
      .process(new SavePropertyProcessor())
      //validate user
      .setProperty("tokenKey", constant(jwtKey))
      .process(new ValidateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          //get user from database
          .process(new PrepareUserDBstatementProcessor())
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          // Check old password i korrect
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              BasicDBObject DbUser = exchange.getIn().getBody(BasicDBObject.class);
              boolean passwordIsCorrect = BCrypt.checkpw((String) exchange.getProperty("oldPassword"), DbUser.getString("password"));
              exchange.setProperty("userAuth", passwordIsCorrect);
            }
          })
          .choice()
            .when(exchangeProperty("userAuth").isEqualTo(true))
              //change password and reinsert into database
              .process(new UpdatePasswordProcessor())
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
              .setBody(simple("Password is updated"))
          .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
          .endChoice()
      .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      //get licenses of one user
        from("direct:getLicense")
        //validate user
        .setProperty("tokenKey", constant(jwtKey))
        .process(new ValidateTokenProcessor())
        .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
            //get user from database
            .process(new PrepareUserDBstatementProcessor())
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
            .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                //Retrieve list of licenseID's from user, and retrieve those licenses from database
                .process(new GetLicenseProcessor())
                .process(exchange -> {
                ArrayList<Bson> bsons = (ArrayList<Bson>) exchange.getProperty("bsons");
                Bson criteria = Filters.or(bsons);
                exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
                .choice()
                .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                    //trim fields from licenses the user shouldn't see.
                    .process(exchange -> {
                        ArrayList<BasicDBObject> licensesList = exchange.getIn().getBody(ArrayList.class);
                        for(int i = 0; i < licensesList.size(); i++){
                            BasicDBObject license = new BasicDBObject(licensesList.get(i));
                            license.removeField("_id");
                            license.removeField("licenceID");
                            license.removeField("highQuality");
                            license.removeField("originalStartDate");
                            license.removeField("groupLicenseFlag");
                            licensesList.set(i, license);
                        }
                    exchange.getIn().setBody(licensesList);
                    })
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .endChoice()
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .endChoice()
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      // Admin Endpoints
      // GET USER
      from("direct:adminGetUser")
      //validate user
      .setProperty("tokenKey", constant(jwtKey))
      .process(new SavePropertyProcessor())
      .process( new ValidateTokenProcessor())
      .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true), exchangeProperty("userRole").isEqualTo("admin")))
          //get user from database (not the logged in user, but the user the admin wants to get) and trim fields the admin shouldn't see.
          .process(new PrepareUserDBstatementProcessor())
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .process(exchange -> {
            BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
            user.removeField("password");
            user.removeField("role");
            user.removeField("_id");
            exchange.getIn().setBody(user);
          })
        .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

        // UPDATE USER
        from("direct:adminUpdateUser")
        //validate user
        .setProperty("tokenKey", constant(jwtKey))
        .process(new ValidateTokenProcessor())
        .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true), exchangeProperty("userRole").isEqualTo("admin")))
            //get user object from database
            .process(exchange -> {
                User user = exchange.getIn().getBody(User.class);
                exchange.setProperty("user", user);
                exchange.setProperty("usersEmail", user.getOldEmail());
            })
            .setBody(exchangeProperty("user"))
            .process(new PrepareUserDBstatementProcessor())
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
            .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                //update local user object according to body and reinsert into database.
                .process(new UpdateUserProcessor())
                .setProperty("newUser", simple("${body}"))
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
                .process(new AdminSendUserProcessor())
                .setBody(exchangeProperty("newUser"))
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .endChoice()
        .otherwise()
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

        //UPDATE ROLE
        from("direct:adminUpdateUserRole")
        //validate user
        .setProperty("tokenKey", constant(jwtKey))
        .process(new ValidateTokenProcessor())
        .choice()
            .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true), exchangeProperty("userRole").isEqualTo("admin")))
                //get user from database
                .process(new SavePropertyProcessor())
                .process(new PrepareUserDBstatementProcessor())
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
                .choice()
                .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                    //update role on user and reinsert into database
                    .process(new UpdateRoleProcessor())
                    .setProperty("newUser", simple("${body}"))
                    .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
                    .process(new AdminSendUserProcessor())
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .endChoice()
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

        //FLAG LICENSE DELETED
        from("direct:flagLicense")
        .setProperty("tokenKey", constant(jwtKey))
        .process(new ValidateTokenProcessor())
        .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true), exchangeProperty("userRole").isEqualTo("admin")))
            //get license from database
            .process(new SavePropertyProcessor())
            .process(exchange -> {
                String licenseID = (String) exchange.getProperty("licenseID");
                Bson criteria = Filters.eq("licenseID", licenseID);
                exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                })
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
            .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                //set deleted flag and reinsert into database
                .process(new FlagLicenseDeletedProcessor())
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=save")
            .otherwise()
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .endChoice()
        .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));


        from("quartz2:testTimer?cron=0+0+0+?+*+*+*")//Trigger at midnight every day, use for prod.
        //from("quartz2:testTimer?cron=0+*+*+?+*+*")//Trigger every minute, use for test/dev.
        .log("cronjob triggered")
        .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Bson criteria = Filters.eq("status", true);
                exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
        })
        .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
        .process(new GetExpiredLicensesProcessor())
        .choice()
            .when(exchangeProperty("LicensesToDisable").isEqualTo(true))
            .setProperty("flagToChange", constant("status"))
            .setProperty("flagValue", constant(false))
            .process(new prepareLicenseDisableDBstatementProcessor())
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=update");
    }
}
