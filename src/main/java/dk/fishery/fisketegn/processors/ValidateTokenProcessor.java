package dk.fishery.fisketegn.processors;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Value;

public class ValidateTokenProcessor implements Processor {

  @Override
  public void process(Exchange exchange) throws Exception {
    String tokenKey = (String) exchange.getProperty("tokenKey");
    String token = (String) exchange.getIn().getHeader("fiskeToken");

    try {
      Jws<Claims> jws = Jwts.parser()
      .setSigningKey(tokenKey)
      .parseClaimsJws(token);

      exchange.setProperty("userEmail", jws.getBody().get("id"));
      exchange.setProperty("userRole", jws.getBody().get("role"));
      exchange.setProperty("tokenIsValidated", true);
    } catch (Exception e) {
      exchange.setProperty("tokenIsValidated", false);
    }
  }
}
