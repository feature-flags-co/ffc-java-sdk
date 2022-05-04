package co.featureflags.server;

import co.featureflags.commons.model.AllFlagStates;
import co.featureflags.commons.model.EvalDetail;
import co.featureflags.commons.model.FFCUser;
import co.featureflags.commons.model.FlagState;
import co.featureflags.commons.model.VariationParams;
import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.DefaultSender;
import co.featureflags.server.exterior.FFCClient;
import co.featureflags.server.exterior.HttpConfig;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class Demos {

    static void monitoringPerf(String name, Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        System.out.printf("execution time for %s is %d milliseconds%n", name, duration.toMillis());
    }

    static final class FFCClientStartAndWait {
        public static void main(String[] args) throws IOException {
            String envSecret = "ZDMzLTY3NDEtNCUyMDIxMTAxNzIxNTYyNV9fMzZfXzQ2X185OF9fZGVmYXVsdF80ODEwNA==";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://api-dev.featureflag.co");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://api-dev.featureflag.co");


            FFCConfig config = new FFCConfig.Builder()
                    .offline(false)
                    .updateProcessorFactory(streamingBuilder)
                    .insightProcessorFactory(insightProcessorBuilder)
                    .build();

            FFCClient client = new FFCClientImp(envSecret, config);

            Scanner scanner = new Scanner(System.in);
            FFCUser user;
            String line;
            while (client.isInitialized()) {
                System.out.println("------------------------------");
                System.out.println("input user key and flag key seperated by /");
                line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                try {
                    String[] words = line.split("/");
                    user = new FFCUser.Builder(words[0]).userName(words[0]).build();

                    Instant start = Instant.now();
                    FlagState<String> res = client.variationDetail(words[1], user, "Not Found");
                    Instant end = Instant.now();
                    System.out.println("result is " + res);
                    monitoringPerf("evaluate", start, end);
                    Random rd = new Random();
                    if (rd.nextBoolean()) {
                        System.out.println("input event name");
                        line = scanner.nextLine();
                        client.trackMetric(user, line);
                    }
                } catch (Exception e) {
                    break;
                }
            }
            scanner.close();
            client.close();
            System.out.println("APP FINISHED");

        }
    }

    static final class FFCClientStartNotWait {
        public static void main(String[] args) throws InterruptedException, IOException {
            String envSecret = "ZDMzLTY3NDEtNCUyMDIxMTAxNzIxNTYyNV9fMzZfXzQ2X185OF9fZGVmYXVsdF80ODEwNA==";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://api-dev.featureflag.co");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://api-dev.featureflag.co");


            FFCConfig config = new FFCConfig.Builder()
                    .startWaitTime(Duration.ZERO)
                    .updateProcessorFactory(streamingBuilder)
                    .insightProcessorFactory(insightProcessorBuilder)
                    .build();

            FFCClient client = new FFCClientImp(envSecret, config);

            Scanner scanner = new Scanner(System.in);
            FFCUser user;
            String line;
            while (client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(15))) {
                System.out.println("------------------------------");
                System.out.println("input user key and flag key seperated by /");
                line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                try {
                    String[] words = line.split("/");
                    user = new FFCUser.Builder(words[0]).userName(words[0]).build();
                    Instant start = Instant.now();
                    FlagState<String> res = client.variationDetail(words[1], user, "Not Found");
                    Instant end = Instant.now();
                    System.out.println("result is " + res);
                    monitoringPerf("evaluate", start, end);
                } catch (Exception e) {
                    break;
                }
            }
            scanner.close();
            client.close();
            System.out.println("APP FINISHED");
        }
    }

    static final class FFCClientGetAllFlagStates {
        public static void main(String[] args) throws IOException {
            String envSecret = "ZDMzLTY3NDEtNCUyMDIxMTAxNzIxNTYyNV9fMzZfXzQ2X185OF9fZGVmYXVsdF80ODEwNA==";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://api-dev.featureflag.co");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://api-dev.featureflag.co");

            FFCConfig config = new FFCConfig.Builder()
                    .updateProcessorFactory(streamingBuilder)
                    .insightProcessorFactory(insightProcessorBuilder)
                    .build();

            FFCClient client = new FFCClientImp(envSecret, config);
            Scanner scanner = new Scanner(System.in);
            FFCUser user;
            String line;
            String latestUserKey = "";
            AllFlagStates<String> allFlagStates = null;
            while (client.isInitialized()) {
                System.out.println("------------------------------");
                System.out.println("input user key and flag key seperated by /");
                line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                try {
                    String[] words = line.split("/");
                    Instant start = Instant.now();
                    if (latestUserKey.equals(words[0]) && allFlagStates != null) {
                        System.out.println("result is " + allFlagStates.get(words[1]));
                    } else {
                        user = new FFCUser.Builder(words[0]).userName(words[0]).build();
                        allFlagStates = client.getAllLatestFlagsVariations(user);
                        System.out.println("result is " + allFlagStates.get(words[1]));
                        latestUserKey = words[0];
                    }
                    Instant end = Instant.now();
                    monitoringPerf("evaluate", start, end);
                } catch (Exception e) {
                    break;
                }
            }
            scanner.close();
            client.close();
            System.out.println("APP FINISHED");
        }
    }

}
