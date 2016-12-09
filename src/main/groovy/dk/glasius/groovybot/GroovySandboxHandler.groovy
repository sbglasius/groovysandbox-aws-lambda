package dk.glasius.groovybot

import com.amazonaws.services.lambda.runtime.Context
import groovy.transform.Synchronized
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.kohsuke.groovy.sandbox.SandboxTransformer

class GroovySandboxHandler {
    private static String ENCODING = 'UTF-8'
    private static String[] FILTERED_STACKTRACE_ELEMENTS = [
            'com.google.', 'org.mortbay.',
            'java.', 'javax.', 'sun.',
            'groovy.', 'org.codehaus.groovy.',
            'executor', 'org.kohsuke.', 'dk.glasius',
            'lambdainternal.', 'org.spockframework'
    ]


    GroovyShell sh
    GroovySandboxedEnvironment groovySandboxedEnvironment = new GroovySandboxedEnvironment()

    Map executeScriptHandler(Map data, Context context) {
        context.logger.log "received in groovy: $data"
        if(data.script) {
            def result = executeScript(data.script as String)
            [executionResult: result.executionResult, outputText: result.outputText, stacktraceText: result.stacktraceText]
        } else {
            return [error: 'No Script']
        }
    }


    @Synchronized
    // Only one process can take over System.out and System.err at the time
    private GroovyExecutionResult executeScript(String script) {

        def stream = new ByteArrayOutputStream()
        def printStream = new PrintStream(stream, true, ENCODING)

        def stacktrace = new StringWriter()
        def errWriter = new PrintWriter(stacktrace)

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(new SandboxTransformer())
        def binding = new Binding([out: printStream])
        sh = new GroovyShell(binding, cc)
        groovySandboxedEnvironment.register()

        def emcEvents = []
        def listener = { MetaClassRegistryChangeEvent event ->
            emcEvents << event
        } as MetaClassRegistryChangeEventListener

        GroovySystem.metaClassRegistry.addMetaClassRegistryChangeEventListener listener

        def originalOut = System.out
        def originalErr = System.err

        System.setOut(printStream)
        System.setErr(printStream)

        def result = ""
        try {
            result = sh.evaluate(script)
        } catch (MultipleCompilationErrorsException e) {
            stacktrace.append(e.message - 'startup failed, Script1.groovy: ')
        } catch (Throwable t) {
            sanitizeStacktrace(t)
            def cause = t
            while (cause = cause?.cause) {
                sanitizeStacktrace(cause)
            }
            t.printStackTrace(errWriter)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)

            GroovySystem.metaClassRegistry.removeMetaClassRegistryChangeEventListener listener
            emcEvents.each { MetaClassRegistryChangeEvent event ->
                GroovySystem.metaClassRegistry.removeMetaClass event.clazz
            }
            groovySandboxedEnvironment.unregister()
            println groovySandboxedEnvironment.sw
        }
        new GroovyExecutionResult(
                executionResult: "${result}",
                outputText: "${stream.toString(ENCODING)}",
                stacktraceText: "${escape(stacktrace)}"
        )
    }

    String escape(object) {
        if (object) {
            object.toString().replaceAll(/"/, /\\"/)
            // Need to explicitly check for value to display to user so that user knows what type of falsey value it is
        } else if (object == false) {
            "false"
        } else if (object == null) {
            "null"
        } else if (object == 0) {
            "0"
        } else if (object == "") {
            "\"\""
        } else if (object == []) {
            "[]"
        } else if (object == [:]) {
            "[:]"
        } else {
            "N/A"
        }
    }

    def sanitizeStacktrace(Throwable t) {
        StackTraceElement[] cleanedTrace = t.stackTrace.findAll { stackTraceElement ->
            FILTERED_STACKTRACE_ELEMENTS.every { !stackTraceElement.className.startsWith(it) }
        }
        t.stackTrace = cleanedTrace
    }


}
