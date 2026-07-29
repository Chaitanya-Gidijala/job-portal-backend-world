import os
import re

file_path = r"src\main\java\com\job\portal\service\CodeExecutionService.java"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update execute method
new_execute = """            if ("java".equalsIgnoreCase(language)) {
                return executeJava(tempDir, code, testCases);
            } else if ("python".equalsIgnoreCase(language)) {
                return executePython(tempDir, code, testCases);
            } else if ("javascript".equalsIgnoreCase(language)) {
                return executeJavascript(tempDir, code, testCases);
            } else if ("cpp".equalsIgnoreCase(language)) {
                return executeCpp(tempDir, code, testCases);
            } else {"""
content = content.replace("""            if ("java".equalsIgnoreCase(language)) {
                return executeJava(tempDir, code, testCases);
            } else if ("python".equalsIgnoreCase(language)) {
                return executePython(tempDir, code, testCases);
            } else {""", new_execute)

# 2. Update extractCleanError
old_clean = """            lastLine = line.replace("/app/main.py", "solution.py").replace("Main.java", "Solution.java").trim();"""
new_clean = """            lastLine = line.replace("/app/main.py", "solution.py")
                                 .replace("Main.java", "Solution.java")
                                 .replace("/app/main.js", "solution.js")
                                 .replace("main.cpp", "solution.cpp")
                                 .trim();"""
content = content.replace(old_clean, new_clean)

# 3. Add Javascript and Cpp methods
js_cpp_methods = """
    private CodeExecutionResponse executeJavascript(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases) throws IOException, InterruptedException {
        File mainFile = new File(tempDir.toFile(), "main.js");
        try (FileWriter writer = new FileWriter(mainFile)) {
            writer.write(code + "\\n\\n");
            for (int i = 0; i < testCases.size(); i++) {
                CodeExecutionRequest.TestCase tc = testCases.get(i);
                writer.write("console.log('@@@TC" + i + "_START@@@');\\n");
                writer.write("try {\\n");
                String input = tc.getInput();
                if (input != null && input.contains("]")) {
                    String arrayPart = input.substring(0, input.indexOf("]") + 1);
                    String targetPart = input.substring(input.indexOf("]") + 1).trim();
                    writer.write("    let res = twoSum(" + arrayPart + ", " + targetPart + ");\\n");
                    writer.write("    console.log(JSON.stringify(res).replace(/ /g, ''));\\n");
                }
                writer.write("} catch (e) {\\n");
                writer.write("    console.error(e);\\n");
                writer.write("} finally {\\n");
                writer.write("    console.log('@@@TC" + i + "_END@@@');\\n");
                writer.write("}\\n");
            }
        }

        try {
            dockerClient.inspectImageCmd("node:18-alpine").exec();
        } catch (Exception e) {
            dockerClient.pullImageCmd("node:18-alpine").start().awaitCompletion(5, TimeUnit.MINUTES);
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(tempDir.toAbsolutePath().toString(), new Volume("/app")));

        CreateContainerResponse container = dockerClient.createContainerCmd("node:18-alpine")
                .withHostConfig(hostConfig)
                .withWorkingDir("/app")
                .withCmd("node", "main.js")
                .withNetworkDisabled(true)
                .withMemory(128 * 1024 * 1024L)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();
        
        WaitContainerResultCallback callback = new WaitContainerResultCallback();
        dockerClient.waitContainerCmd(container.getId()).exec(callback);
        Integer exitCode = callback.awaitStatusCode(10, TimeUnit.SECONDS);

        String output = fetchContainerLogs(container.getId());
        dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();

        if (exitCode != null && exitCode != 0 && !output.contains("@@@TC0_START@@@")) {
            return CodeExecutionResponse.builder()
                    .status("error")
                    .message("Runtime Error")
                    .caseResults(List.of(CodeExecutionResponse.CaseResult.builder()
                            .passed(false)
                            .actualOutput(extractCleanError(output))
                            .build()))
                    .build();
        }

        return parseTestResults(output, testCases);
    }

    private CodeExecutionResponse executeCpp(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases) throws IOException, InterruptedException {
        File mainFile = new File(tempDir.toFile(), "main.cpp");
        try (FileWriter writer = new FileWriter(mainFile)) {
            writer.write("#include <iostream>\\n#include <vector>\\n#include <unordered_map>\\nusing namespace std;\\n\\n");
            writer.write(code + "\\n\\n");
            writer.write("int main() {\\n");
            writer.write("    Solution sol;\\n");
            for (int i = 0; i < testCases.size(); i++) {
                CodeExecutionRequest.TestCase tc = testCases.get(i);
                writer.write("    try {\\n");
                writer.write("        cout << \\"@@@TC" + i + "_START@@@\\" << endl;\\n");
                String input = tc.getInput();
                if (input != null && input.contains("]")) {
                    String arrayPart = input.substring(1, input.indexOf("]"));
                    String targetPart = input.substring(input.indexOf("]") + 1).trim();
                    writer.write("        vector<int> nums = {" + arrayPart + "};\\n");
                    writer.write("        int target = " + targetPart + ";\\n");
                    writer.write("        vector<int> res = sol.twoSum(nums, target);\\n");
                    writer.write("        if (res.size() >= 2) cout << \\"[\\" << res[0] << \\",\\" << res[1] << \\"]\\" << endl;\\n");
                    writer.write("        else cout << \\"[]\\" << endl;\\n");
                }
                writer.write("    } catch (...) {\\n");
                writer.write("        cout << \\"Exception caught\\" << endl;\\n");
                writer.write("    }\\n");
                writer.write("    cout << \\"@@@TC" + i + "_END@@@\\" << endl;\\n");
            }
            writer.write("    return 0;\\n");
            writer.write("}\\n");
        }

        try {
            dockerClient.inspectImageCmd("gcc:latest").exec();
        } catch (Exception e) {
            dockerClient.pullImageCmd("gcc:latest").start().awaitCompletion(5, TimeUnit.MINUTES);
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(tempDir.toAbsolutePath().toString(), new Volume("/app")));

        CreateContainerResponse container = dockerClient.createContainerCmd("gcc:latest")
                .withHostConfig(hostConfig)
                .withWorkingDir("/app")
                .withCmd("sh", "-c", "g++ -O2 main.cpp && ./a.out")
                .withNetworkDisabled(true)
                .withMemory(256 * 1024 * 1024L)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();
        
        WaitContainerResultCallback callback = new WaitContainerResultCallback();
        dockerClient.waitContainerCmd(container.getId()).exec(callback);
        Integer exitCode = callback.awaitStatusCode(15, TimeUnit.SECONDS);

        String output = fetchContainerLogs(container.getId());
        dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();

        if (exitCode != null && exitCode != 0 && !output.contains("@@@TC0_START@@@")) {
            return CodeExecutionResponse.builder()
                    .status("error")
                    .message("Compilation Error")
                    .caseResults(List.of(CodeExecutionResponse.CaseResult.builder()
                            .passed(false)
                            .actualOutput(extractCleanError(output))
                            .build()))
                    .build();
        }

        return parseTestResults(output, testCases);
    }
"""

content = content.replace("    private String extractCleanError(String output) {", js_cpp_methods + "\n    private String extractCleanError(String output) {")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Updated successfully.")
