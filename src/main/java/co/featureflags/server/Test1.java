package co.featureflags.server;

import com.google.common.base.MoreObjects;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

public class Test1 {
    public static void main(String[] args) {
        String json = "{\"name\": null, \"age\": 12}";
        Person p = JsonHelper.deserialize(json, Person.class);
        System.out.println(p.name);
        Instant instant1 = Instant.now();
        double res = VariationSplittingAlgorithm.percentageOfKey("kkkkk");
        Instant instant2 = Instant.now();
        System.out.println(res +" " + Duration.between(instant1, instant2).toMillis());
    }

    static class Person {
        Name name;
        Integer age;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("age", age)
                    .toString();
        }
    }

    static class Name {
        String firstName;
        String lastName;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("firstName", firstName)
                    .add("lastName", lastName)
                    .toString();
        }
    }

}
