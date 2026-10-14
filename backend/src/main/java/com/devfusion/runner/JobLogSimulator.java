package com.devfusion.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模拟各类流水线任务的执行日志。
 * 本地演示实现：不真实执行构建命令，日志按各插件类型生成，shell 脚本逐行回显；
 * 失败控制：SHELL 脚本包含 "exit 1"，或 config 中 "fail": true / "failJobs": N。
 */
public class JobLogSimulator {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record Script(List<String> lines, boolean fail, long simulateSeconds) {
    }

    public static Script simulate(RunJob job, Map<String, String> vars, Map<String, Boolean> secret) {
        String cfg = job.getConfig() == null ? "{}" : job.getConfig();
        long simulateSeconds = 0;
        try {
            JsonNode c = JSON.readTree(cfg);
            if (c.hasNonNull("simulateSeconds")) {
                simulateSeconds = c.get("simulateSeconds").asLong();
            }
            Script script = switch (job.getType() == null ? "" : job.getType()) {
                case "SHELL" -> shell(job.getName(), c, vars, secret);
                case "MAVEN_BUILD" -> maven(c);
                case "NPM_BUILD" -> npm(c);
                case "DOCKER_BUILD" -> docker(c);
                case "UNIT_TEST" -> unitTest(c);
                case "ARTIFACT_UPLOAD" -> artifactUpload(c);
                case "APPROVAL" -> new Script(List.of("[info] 等待人工审批..."), false, 0);
                default -> new Script(List.of("[warn] 未知任务类型 " + job.getType() + "，按成功处理"), false, 0);
            };
            return new Script(script.lines(), script.fail(), simulateSeconds);
        } catch (Exception e) {
            return new Script(List.of("[error] 配置解析失败: " + e.getMessage()), true, 0);
        }
    }

    /** 日志头部的系统/用户变量注入（密文打码） */
    public static List<String> envHeader(Map<String, String> vars, Map<String, Boolean> secret) {
        List<String> lines = new ArrayList<>();
        lines.add("----- 构建环境变量 -----");
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                boolean sec = secret != null && Boolean.TRUE.equals(secret.get(e.getKey()));
                lines.add("[env] " + e.getKey() + "=" + (sec ? "********" : e.getValue()));
            }
        }
        lines.add("----- 任务输出 -----");
        return lines;
    }

    private static Script shell(String name, JsonNode c, Map<String, String> vars, Map<String, Boolean> secret) {
        List<String> lines = new ArrayList<>();
        lines.add("$ bash task/" + name.toLowerCase().replace(' ', '-'));
        String script = c.hasNonNull("script") ? c.get("script").asText() : "";
        boolean fail = false;
        for (String raw : script.split("\n")) {
            String cmd = raw.trim();
            if (cmd.isEmpty() || cmd.startsWith("#")) {
                continue;
            }
            cmd = expandVars(cmd, vars, secret);
            lines.add("$ " + cmd);
            if (cmd.contains("exit 1")) {
                lines.add("[error] 脚本以 exit 1 退出");
                fail = true;
            } else if (cmd.startsWith("echo ")) {
                lines.add(cmd.substring(5).replace("\"", ""));
            } else if (cmd.startsWith("cd ") || cmd.startsWith("mkdir ") || cmd.startsWith("cp ")
                    || cmd.startsWith("mv ") || cmd.startsWith("chmod ")) {
                // 常见文件操作命令静默通过
            } else if (!cmd.isEmpty()) {
                lines.add("[info] 命令执行完成");
            }
        }
        lines.add("[info] 任务完成");
        return new Script(lines, fail, 0);
    }

    /** ${VAR} 替换；密文变量在回显中打码 */
    private static String expandVars(String cmd, Map<String, String> vars, Map<String, Boolean> secret) {
        if (vars == null || vars.isEmpty() || !cmd.contains("${")) {
            return cmd;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < cmd.length()) {
            int start = cmd.indexOf("${", i);
            if (start < 0) {
                out.append(cmd.substring(i));
                break;
            }
            int end = cmd.indexOf("}", start);
            if (end < 0) {
                out.append(cmd.substring(i));
                break;
            }
            out.append(cmd, i, start);
            String key = cmd.substring(start + 2, end);
            if (vars.containsKey(key)) {
                boolean sec = secret != null && Boolean.TRUE.equals(secret.get(key));
                out.append(sec ? "********" : vars.get(key));
            } else {
                out.append(cmd, start, end + 1);
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static Script maven(JsonNode c) {
        boolean fail = c.path("fail").asBoolean(false);
        List<String> lines = new ArrayList<>();
        String goals = c.hasNonNull("goals") ? c.get("goals").asText() : "clean package";
        lines.add("$ mvn " + goals + " -B");
        lines.add("[INFO] Scanning for projects...");
        lines.add("[INFO] Building devfusion-app 1.0.0-SNAPSHOT");
        lines.add("[INFO] --- maven-compiler-plugin: compile ---");
        lines.add("[INFO] Compiling 128 source files");
        if (fail) {
            lines.add("[ERROR] COMPILATION ERROR : 模块中存在无法解析的符号");
            lines.add("[ERROR] BUILD FAILURE");
        } else {
            lines.add("[INFO] --- maven-jar-plugin: devfusion-app.jar ---");
            lines.add("[INFO] BUILD SUCCESS");
        }
        return new Script(lines, fail, 0);
    }

    private static Script npm(JsonNode c) {
        boolean fail = c.path("fail").asBoolean(false);
        List<String> lines = new ArrayList<>();
        lines.add("$ npm ci && npm run build");
        lines.add("added 1024 packages in 8s");
        lines.add("> vite build");
        if (fail) {
            lines.add("[error] Build failed: 模块解析错误");
        } else {
            lines.add("✓ 342 modules transformed");
            lines.add("dist/index.html  1.2 kB");
            lines.add("dist/assets/index.js  386.4 kB");
            lines.add("Build complete");
        }
        return new Script(lines, fail, 0);
    }

    private static Script docker(JsonNode c) {
        boolean fail = c.path("fail").asBoolean(false);
        List<String> lines = new ArrayList<>();
        String tag = c.hasNonNull("tag") ? c.get("tag").asText() : "latest";
        lines.add("$ docker build -t " + tag + " .");
        lines.add("Step 1/6 : FROM eclipse-temurin:17-jre");
        lines.add("Step 2/6 : WORKDIR /app");
        lines.add("Step 3/6 : COPY target/*.jar app.jar");
        lines.add("Step 4/6 : EXPOSE 8080");
        if (fail) {
            lines.add("[error] Step 5/6 failed: 镜像层写入失败");
        } else {
            lines.add("Step 5/6 : ENTRYPOINT java -jar app.jar");
            lines.add("Successfully tagged " + tag);
            lines.add("$ docker push " + tag);
            lines.add("Pushed to internal registry");
        }
        return new Script(lines, fail, 0);
    }

    private static Script unitTest(JsonNode c) {
        int cases = c.path("testCases").asInt(24);
        int failJobs = c.path("failJobs").asInt(0);
        if (c.path("fail").asBoolean(false) && failJobs == 0) {
            failJobs = Math.max(1, cases / 10);
        }
        List<String> lines = new ArrayList<>();
        lines.add("$ 运行单元测试");
        lines.add("Tests run: " + cases + ", Failures: " + failJobs + ", Errors: 0, Skipped: 0");
        double passRate = cases == 0 ? 100.0 : (cases - failJobs) * 100.0 / cases;
        boolean gateFail = false;
        if (c.hasNonNull("passRateThreshold")) {
            double threshold = c.get("passRateThreshold").asDouble();
            gateFail = passRate < threshold;
            lines.add(String.format("[gate] 质量门禁：通过率 %.1f%%，要求 >= %.1f%%%s",
                    passRate, threshold, gateFail ? "，未达标，门禁阻断" : "，达标放行"));
        }
        if (failJobs > 0 && !gateFail) {
            lines.add("[error] " + failJobs + " 个用例未通过，质量门禁阻断");
        } else if (!gateFail) {
            lines.add("[info] 全部用例通过，覆盖率 78%");
        }
        return new Script(lines, failJobs > 0 || gateFail, 0);
    }

    private static Script artifactUpload(JsonNode c) {
        List<String> lines = new ArrayList<>();
        String type = c.hasNonNull("artifactType") ? c.get("artifactType").asText() : "JAR";
        lines.add("$ 上传制品至内部制品库");
        lines.add("[info] 制品类型: " + type);
        lines.add("[info] 上传完成，校验和已记录");
        return new Script(lines, false, 0);
    }

    public static String ts() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
