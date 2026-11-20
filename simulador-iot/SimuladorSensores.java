import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * SIMULADOR DE SENSORES - Solo Vivo (Parte 6 - Simulacao de IoT)
 *
 * Complementa o ESP32 do Wokwi (projeto solo-vivo-arduino, via MQTT): o circuito tem um unico sensor
 * de solo (Canteiro 2); este simulador alimenta os outros canteiros por HTTP. Sem o Wokwi, simula tudo.
 *
 * Simula o ESP32 "ESP32 Horta" do dump, com 5 sensores:
 *   - Sensor de solo 01/02/03 (capacitivo) : umidade do solo em %  -> Canteiros 2, 1 e 3
 *   - Temperatura do ar (DHT22)             : temperatura em °C
 *   - Umidade do ar (DHT22)                 : umidade relativa do ar em %
 *
 * As leituras sao enviadas em JSON para POST /api/iot/leituras com o header X-Device-Token.
 * A API grava, gera alertas e decide a irrigacao automatica; a resposta mostra cada decisao.
 *
 * Requer apenas Java 21 (sem dependencias). Uso:
 *   java simulador-iot/SimuladorSensores.java [opcoes]
 *
 * Opcoes:
 *   --cenario  normal | seco | secando | encharcado | calor | sem-leitura   (padrao: seco)
 *   --api      URL base da API                                          (padrao: http://localhost:8080/api)
 *   --token    token do dispositivo                                     (padrao: demo-token-esp32-01)
 *   --ciclos   quantas vezes enviar                                     (padrao: 1)
 *   --intervalo segundos entre envios                                   (padrao: 5)
 */
public class SimuladorSensores {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        Map<String, String> op = new LinkedHashMap<>(Map.of(
                "cenario", "seco",
                "api", "http://localhost:8080/api",
                "token", "demo-token-esp32-01",
                "ciclos", "1",
                "intervalo", "5"));
        for (int i = 0; i + 1 < args.length; i += 2) {
            op.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        String cenario = op.get("cenario");
        int ciclos = Integer.parseInt(op.get("ciclos"));
        int intervalo = Integer.parseInt(op.get("intervalo"));

        System.out.println("=== Simulador Solo Vivo | cenario=" + cenario + " | api=" + op.get("api") + " ===");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        for (int ciclo = 0; ciclo < ciclos; ciclo++) {
            String corpo = montarJson(gerarLeituras(cenario, ciclo));
            System.out.println("\n[" + LocalTime.now().format(HORA) + "] Ciclo " + (ciclo + 1) + "/" + ciclos);
            System.out.println("  -> enviando: " + corpo);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(op.get("api") + "/iot/leituras"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-Device-Token", op.get("token"))
                        .POST(HttpRequest.BodyPublishers.ofString(corpo))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("  <- HTTP " + resp.statusCode() + ": " + resp.body());
            } catch (Exception e) {
                System.out.println("  !! API indisponivel: " + e.getMessage());
            }
            if (ciclo + 1 < ciclos) {
                Thread.sleep(intervalo * 1000L);
            }
        }
    }

    /** Valores por cenario. Os do cenario "seco" sao os do plano de testes (CT-04, CT-06, CT-09). */
    static List<Object[]> gerarLeituras(String cenario, int ciclo) {
        List<Object[]> l = new ArrayList<>();
        switch (cenario) {
            case "seco" -> { // CT-04 / CT-06 / CT-09: solo 24%, 31 °C, ar 62% (sensor 03 segue sem enviar: CT-05)
                l.add(new Object[] {"Sensor de solo 01", 24.0});
                l.add(new Object[] {"Sensor de solo 02", 41.0});
                l.add(new Object[] {"Temperatura do ar", 31.0});
                l.add(new Object[] {"Umidade do ar", 62.0});
            }
            case "secando" -> { // solo do Canteiro 2 cai 3% por ciclo: cruza o minimo (30%) e liga a bomba
                l.add(new Object[] {"Sensor de solo 01", Math.max(5, 40.0 - 3 * ciclo)});
                l.add(new Object[] {"Sensor de solo 02", ruido(42, 2)});
                l.add(new Object[] {"Temperatura do ar", ruido(30, 1)});
                l.add(new Object[] {"Umidade do ar", ruido(60, 3)});
            }
            case "encharcado" -> { // acima do maximo (60%): desliga a bomba e alerta
                l.add(new Object[] {"Sensor de solo 01", 70.0});
                l.add(new Object[] {"Sensor de solo 02", 68.0});
                l.add(new Object[] {"Temperatura do ar", 24.0});
                l.add(new Object[] {"Umidade do ar", 85.0});
            }
            case "calor" -> { // temperatura acima de 35 °C: alerta de alta temperatura
                l.add(new Object[] {"Sensor de solo 01", ruido(40, 2)});
                l.add(new Object[] {"Temperatura do ar", 37.5});
                l.add(new Object[] {"Umidade do ar", 35.0});
            }
            case "sem-leitura" -> { // o Sensor de solo 03 nao envia nada: Canteiro 3 continua "Sem leitura"
                l.add(new Object[] {"Sensor de solo 01", ruido(45, 3)});
                l.add(new Object[] {"Sensor de solo 02", ruido(42, 3)});
                l.add(new Object[] {"Temperatura do ar", ruido(27, 2)});
                l.add(new Object[] {"Umidade do ar", ruido(60, 5)});
            }
            default -> { // normal: tudo dentro da faixa (30%-60%)
                l.add(new Object[] {"Sensor de solo 01", ruido(45, 3)});
                l.add(new Object[] {"Sensor de solo 02", ruido(42, 3)});
                l.add(new Object[] {"Sensor de solo 03", ruido(40, 3)});
                l.add(new Object[] {"Temperatura do ar", ruido(27, 2)});
                l.add(new Object[] {"Umidade do ar", ruido(60, 5)});
            }
        }
        return l;
    }

    static double ruido(double base, double amplitude) {
        return Math.round((base + (RANDOM.nextDouble() * 2 - 1) * amplitude) * 10.0) / 10.0;
    }

    static String montarJson(List<Object[]> leituras) {
        StringBuilder sb = new StringBuilder("{\"leituras\":[");
        for (int i = 0; i < leituras.size(); i++) {
            Object[] l = leituras.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "{\"sensor\":\"%s\",\"valor\":%.1f}", l[0], (Double) l[1]));
        }
        return sb.append("]}").toString();
    }
}
