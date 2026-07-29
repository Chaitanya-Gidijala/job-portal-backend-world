
 = Get-Content src\main\java\com\job\portal\service\CodeExecutionService.java -Raw
 =  -replace "executeJava\(tempDir, code, testCases\)(","executeJava(tempDir, request)"
 =  -replace "executePython\(tempDir, code, testCases\)(","executePython(tempDir, request)"
 =  -replace "executeJavascript\(tempDir, code, testCases\)(","executeJavascript(tempDir, request)"
 =  -replace "executeCpp\(tempDir, code, testCases\)(","executeCpp(tempDir, request)"

 =  -replace "executeJava\(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases\)(","executeJava(Path tempDir, CodeExecutionRequest request) {
        String code = request.getCode();
        String fullCode = request.getFullCode();
        List<CodeExecutionRequest.TestCase> testCases = request.getTestCases();"

 =  -replace "executePython\(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases\)(","executePython(Path tempDir, CodeExecutionRequest request) {
        String code = request.getCode();
        String fullCode = request.getFullCode();
        List<CodeExecutionRequest.TestCase> testCases = request.getTestCases();"

 =  -replace "executeJavascript\(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases\)(","executeJavascript(Path tempDir, CodeExecutionRequest request) {
        String code = request.getCode();
        String fullCode = request.getFullCode();
        List<CodeExecutionRequest.TestCase> testCases = request.getTestCases();"

 =  -replace "executeCpp\(Path tempDir, String code, List<CodeExecutionRequest.TestCase> testCases\)(","executeCpp(Path tempDir, CodeExecutionRequest request) {
        String code = request.getCode();
        String fullCode = request.getFullCode();
        List<CodeExecutionRequest.TestCase> testCases = request.getTestCases();"

Set-Content src\main\java\com\job\portal\service\CodeExecutionService.java 

