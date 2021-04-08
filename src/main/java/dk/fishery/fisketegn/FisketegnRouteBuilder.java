package dk.fishery.fisketegn;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import dk.fishery.fisketegn.processors.*;
import io.swagger.util.Json;
import org.apache.camel.*;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.CamelMongoDbException;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.apache.camel.component.mongodb.MongoDbConstants.RESULT_PAGE_SIZE;

@Component
public class FisketegnRouteBuilder extends RouteBuilder {

  @Value("${fisketegn.api.path}")
  String contextPath;

  @Value("${server.port}")
  String serverPort;

  @Value("${jwtSecure.key}")
  String jwtKey;

  @Value("${licenseNumber.number}")
  int licenseNumber;

    @Override
    public void configure() throws Exception {
      CamelContext context = new DefaultCamelContext();

      onException(IOException.class)
              .log("?")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("nothing found in query"));
      onException(RuntimeCamelException.class)
              .log("Camel exception!")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("camel exception!"));


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
      .enableCORS(true)
      .produces(MediaType.APPLICATION_JSON)
      .consumes(MediaType.APPLICATION_JSON)
      .bindingMode(RestBindingMode.json)

      // AUTH
      .post("/login")
      .type(User.class)
      .to("direct:doesUserExist")

      .post("/validateToken")
      .to("direct:validateToken")

      .post("/buyLicense")
      .type(User.class)
      .to("direct:findOrCreateUser")

      .post("/updateLicense")
      //.type(User.class)
      .to("direct:updateLicense")

      // USER
      .put("/user")
      .type(User.class)
      .to("direct:updateUser")

      .get("/user")
      .to("direct:getUser")

      .delete("/user")
      .to("direct:deleteUser")

      .post("/updatePassword")
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


      // Auth Endpoints
      from("direct:validateToken")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor());

      from("direct:doesUserExist")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          String email = user.getEmail();
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")

      .setProperty("newBody", simple("${body}"))
      .choice()
        .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
          .log("Bruger eksisterer")
          .process(new checkPasswordProcessor())
          .choice()
            .when(exchangeProperty("userAuth").isEqualTo(true))
              .process(new generateTokenProcessor())
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("Wrong username or password"))
          .endChoice()
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
          .setBody(simple("User dont exist"));

      // FIND OR CREATE USER
      from("direct:findOrCreateUser")
      .streamCaching()
      .routeId("findOrCreateUser")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          String email = user.getEmail();
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .choice()
      .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
      .setProperty("newUser", simple("${body}"))
      .log("Bruger eksistere")
      .process(new generateTokenProcessor())
      // .to("direct:payment")
       .to("direct:createLicense")
      .otherwise()
      .log("Bruger eksistere endnu ikke")
      .to("direct:createUser");

      from("direct:createUser")
      //.to("direct:payment")
      .process(new hashPasswordProcessor())
      // Gem bruger i DB
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert")
      .process(new generateTokenProcessor())
      .to("direct:createLicense");

      from("direct:getLicenseNumber")
      .process(
              new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  Bson criteria = Filters.exists("licenseNumber");
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                }
              })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          ArrayList<Document> licenses = (ArrayList<Document>) exchange.getIn().getBody();
          int maxNumber = -1;
          for(Document l: licenses){
            if (Integer.parseInt(l.getString("licenseNumber")) > maxNumber){
              maxNumber = Integer.parseInt(l.getString("licenseNumber"));
            }
          }
          maxNumber++;
          licenseNumber = maxNumber;
          exchange.setProperty("licenseNumber", licenseNumber);
          System.out.println("sdf");
        }
      });


      from("direct:createLicense")
      .choice()
              .when(constant(licenseNumber).isEqualTo(-1))
              .to("direct:getLicenseNumber")
              .end()

      .process(new createLicenseProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=insert")
      .process(
              new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  User input = (User) exchange.getProperty("oldBody");
                  Bson criteria = Filters.eq("email", input.getEmail());
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                }
              })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
          ArrayList<String> licenses = (ArrayList<String>) user.get("licenses");
          if(licenses == null){
            licenses = new ArrayList<>();
          }
          licenses.add((String) exchange.getProperty("licenseID"));
          user.put("licenses", licenses);
          //user.addLicense((String) exchange.getProperty("licenseID"));
          exchange.getIn().setBody(user);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save");

      from("direct:updateLicense")
              //validate user
              .setProperty("tokenKey", constant(jwtKey))
              .setProperty("licenseID", simple("${body}"))
              .process(new validateTokenProcessor())
              .choice()
                .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
                //get user info
                .process(new Processor() {
                  @Override
                  public void process(Exchange exchange) throws Exception {
                    String email = (String) exchange.getProperty("userEmail");
                    Bson criteria = Filters.eq("email", email);
                    exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                  }
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
                //get list of users licenses
                .process(new Processor() {
                  @Override
                  public void process(Exchange exchange) throws Exception {
                    ArrayList listOfUsers = exchange.getIn().getBody(ArrayList.class);
                    exchange.setProperty("user", listOfUsers.get(0));
                    LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
                    String licenseID = prop.get("licenseID");
                    Bson criteria = Filters.eq("licenseID", licenseID);
                    exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                  }
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
                //extract license and update date
                .process(new updateLicenseProcessor())
                //insert back into db
                .process(new Processor() {
                  @Override
                  public void process(Exchange exchange) throws Exception {
                    LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
                    String licenseID = prop.get("licenseID");
                    Bson criteria = Filters.eq("licenseID", licenseID);
                    exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                  }
                })
                .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=save")
              .otherwise()
              .setBody(constant("login failed"));
      //get license id from body
      //get list of licenses from email in token, check if license belongs to a user
      //update startDate on license

      // Opret fisketegn til brugeren
      // Retuner fisketegn til brugeren
      // Send fisketegn på mail, måske en .process();

      //from("direct:payment");

      // User endpoints

      // UPDATE USER
      from("direct:updateUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          exchange.setProperty("user", user);
        }
      })
      .process(new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          exchange.getIn().setBody(exchange.getProperty("user"));
        }
      })
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new updateUserProcessor())
      .setProperty("newUser", simple("${body}"))
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
      .setBody(exchangeProperty("newUser"))
      .process(new generateTokenProcessor());

      // GET USER
      from("direct:getUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process( new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
          user.removeField("password");
          user.removeField("role");
          user.removeField("_id");
          exchange.getIn().setBody(user);
        }
      });

      // DELETE USER
      from("direct:deleteUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("userEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
              .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
                  exchange.getIn().setBody(user);
                }
              })
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=remove")
              .setBody(simple("User deleted"))
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
              .setBody(simple("User do not exsist"))
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      // UPDATE PASSWORD
      from("direct:updatePassword")
      .process(new savePropertyProcessor())
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new updatePasswordProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
      .setBody(simple("Password is updated"))
      .otherwise()
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
      .setBody(simple("Token invalid"));

      from("direct:getLicense")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("userEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
              .process(new getLicenseProcessor())
              .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  ArrayList<Bson> bsons = (ArrayList<Bson>) exchange.getProperty("bsons");
                  Bson criteria = Filters.or(bsons);
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                }
              })
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
              .choice()
                .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
                  .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                      ArrayList<BasicDBObject> licensesList = exchange.getIn().getBody(ArrayList.class);
                      for(int i = 0; i < licensesList.size(); i++){
                        BasicDBObject license = new BasicDBObject(licensesList.get(i));
                        license.removeField("_id");
                        licensesList.set(i, license);
                      }
                      exchange.getIn().setBody(licensesList);
                    }
                  })
                .otherwise()
                  .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                  .setBody(simple("Endnu ingen fisketegn"))
              .endChoice()
            .otherwise()
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
              .setBody(simple("Kunne ikke finde bruger"));






      // Admin Endpoints

      // GET USER
      from("direct:adminGetUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new savePropertyProcessor())
      .process( new validateTokenProcessor())
      .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true),
        exchangeProperty("userRole").isEqualTo("admin")))
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("usersEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
              user.removeField("password");
              user.removeField("role");
              user.removeField("_id");
              exchange.getIn().setBody(user);
            }
          });


      // UPDATE USER
      from("direct:adminUpdateUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true),
          exchangeProperty("userRole").isEqualTo("admin")))
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              User user = exchange.getIn().getBody(User.class);
              exchange.setProperty("user", user);
              exchange.setProperty("usersEmail", user.getOldEmail());
            }
          })
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              exchange.getIn().setBody(exchange.getProperty("user"));
            }
          })
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("usersEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .choice()
        .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
          .process(new updateUserProcessor())
          .setProperty("newUser", simple("${body}"))
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
          .setBody(exchangeProperty("newUser"))
          .process(new generateTokenProcessor())
        .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
          .setBody(simple("Kunne ikke finde bruger"));

      from("direct:adminUpdateUserRole")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true),
          exchangeProperty("userRole").isEqualTo("admin")))
          .process(new savePropertyProcessor())
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("usersEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
            .process(new updateRoleProcessor())
            .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
          .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
            .setBody(simple("Kunne ikke finde bruger"));


      from("direct:flagLicense")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(PredicateBuilder.and(exchangeProperty("tokenIsValidated").isEqualTo(true),
          exchangeProperty("userRole").isEqualTo("admin")))
          .process(new savePropertyProcessor())
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String licenseID = (String) exchange.getProperty("licenseID");
              Bson criteria = Filters.eq("licenseID", licenseID);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=findAll")
          .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
              .process(new FlagLicenseDeletedProcessor())
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=save")
            .otherwise()
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
              .setBody(simple("Kunne ikke finde fisketegn"))
          .endChoice()
        .otherwise()
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
          .setBody(simple("access denied"));
    }
}


