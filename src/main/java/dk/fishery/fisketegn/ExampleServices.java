package dk.fishery.fisketegn;

public class ExampleServices {
  public static void example(MyBean bodyIn){
    bodyIn.setname("Hello, " + bodyIn.getname());
    bodyIn.setId(bodyIn.getId()*10);
  }
}
