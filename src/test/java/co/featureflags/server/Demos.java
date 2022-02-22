package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.commons.model.EvalDetail;
import co.featureflags.commons.model.FFCUser;
import co.featureflags.commons.model.VariationParams;
import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.DefaultSender;
import co.featureflags.server.exterior.FFCClient;
import co.featureflags.server.exterior.HttpConfig;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

abstract class Demos {

    static void monitoringPerf(String name, Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        System.out.printf("execution time for %s is %d milliseconds%n", name, duration.toMillis());
    }

    static final class FFCClientStartAndWait {
        public static void main(String[] args) throws IOException {
            String envSecret = "YjA1LTNiZDUtNCUyMDIxMDkwNDIyMTMxNV9fMzhfXzQ4X18xMDNfX2RlZmF1bHRfNzc1Yjg=";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://ffc-api-ce2-dev.chinacloudsites.cn");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://ffc-api-ce2-dev.chinacloudsites.cn");


            FFCConfig config = new FFCConfig.Builder()
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
                    user = new FFCUser.Builder(words[0]).build();
                    Instant start = Instant.now();
                    EvalDetail<String> res = client.variationDetail(words[1], user, "Not Found");
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

    static final class FFCClientStartNotWait {
        public static void main(String[] args) throws InterruptedException, IOException {
            String envSecret = "YjA1LTNiZDUtNCUyMDIxMDkwNDIyMTMxNV9fMzhfXzQ4X18xMDNfX2RlZmF1bHRfNzc1Yjg=";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://ffc-api-ce2-dev.chinacloudsites.cn");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://ffc-api-ce2-dev.chinacloudsites.cn");


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
                    user = new FFCUser.Builder(words[0]).build();
                    Instant start = Instant.now();
                    EvalDetail<String> res = client.variationDetail(words[1], user, "Not Found");
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

    static final class showJDKWrapper {
        public static void main(String[] args) throws IOException {
            String envSecret = "YjA1LTNiZDUtNCUyMDIxMDkwNDIyMTMxNV9fMzhfXzQ4X18xMDNfX2RlZmF1bHRfNzc1Yjg=";
            BasicConfig basicConfig = new BasicConfig(envSecret, false);
            HttpConfig httpConfig = Factory.httpConfigFactory().createHttpConfig(basicConfig);
            DefaultSender sender = new Senders.DefaultSenderImp(httpConfig, 1, Duration.ofMillis(100));
            String jsonOutput = Resources.toString(Resources.getResource("data.json"), StandardCharsets.UTF_8);
            String feedback = sender.postJson("http://localhost:8080/api/init", jsonOutput);
            System.out.println("------------------------------");
            System.out.println(feedback);
            Scanner scanner = new Scanner(System.in);
            FFCUser user;
            String line;
            while (true) {
                System.out.println("------------------------------");
                System.out.println("input user key and flag key seperated by /");
                line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                try {
                    String[] words = line.split("/");
                    user = new FFCUser.Builder(words[0]).build();
                    VariationParams params = VariationParams.of(words[1], user);
                    String jsonBody = params.jsonfy();
                    System.out.println(jsonBody);
                    String jsonResult = sender.postJson("http://localhost:8080/api/variation", jsonBody);
                    EvalDetail<String> res = EvalDetail.fromJson(jsonResult, String.class);
                    System.out.println("result is " + res);
                } catch (Exception e) {
                    break;
                }
            }
            scanner.close();
            sender.close();
            System.out.println("APP FINISHED");

        }
    }

    static final class AllLatestFlagValuesForGivenUser {
        public static void main(String[] args) throws IOException {
            String envSecret = "YjA1LTNiZDUtNCUyMDIxMDkwNDIyMTMxNV9fMzhfXzQ4X18xMDNfX2RlZmF1bHRfNzc1Yjg=";

            StreamingBuilder streamingBuilder = Factory.streamingBuilder()
                    .newStreamingURI("wss://ffc-api-ce2-dev.chinacloudsites.cn");

            InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
                    .eventUri("https://ffc-api-ce2-dev.chinacloudsites.cn");


            FFCConfig config = new FFCConfig.Builder()
                    .updateProcessorFactory(streamingBuilder)
                    .insightProcessorFactory(insightProcessorBuilder)
                    .build();

            FFCClient client = new FFCClientImp(envSecret, config);
            Scanner scanner = new Scanner(System.in);
            FFCUser user;
            String userkey;
            while (client.isInitialized()) {
                System.out.println("------------------------------");
                System.out.println("input user key");
                userkey = scanner.nextLine();
                if ("exit".equalsIgnoreCase(userkey)) {
                    break;
                }
                try {
                    user = new FFCUser.Builder(userkey).build();
                    Instant start = Instant.now();
                    List<EvalDetail<String>> res = client.getAllLatestFlagsVariations(user);
                    Instant end = Instant.now();
                    for (EvalDetail<String> ed : res) {
                        System.out.println(ed);
                    }
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
