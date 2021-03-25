package dk.fishery.fisketegn.processors;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class validateToken implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    String tokenKey = (String) exchange.getProperty("tokenKey");
    String token = (String) exchange.getIn().getBody();

    try {
      Jws<Claims> jws = Jwts.parser()
      .setSigningKey(tokenKey)
      .parseClaimsJws(token);

      exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
      exchange.getIn().setBody("Token er ok");
    } catch (ExpiredJwtException e) {
      exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 401);
      exchange.getIn().setBody("Token er ikke ok");
    }
  }
}
