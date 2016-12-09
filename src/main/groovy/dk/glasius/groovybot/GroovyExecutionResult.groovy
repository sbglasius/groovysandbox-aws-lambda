package dk.glasius.groovybot

import groovy.transform.Canonical

@Canonical
class GroovyExecutionResult {
    String executionResult
    String outputText
    String stacktraceText
}
