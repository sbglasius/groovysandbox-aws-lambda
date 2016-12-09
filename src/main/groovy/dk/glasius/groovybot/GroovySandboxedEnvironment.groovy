package dk.glasius.groovybot

import groovy.util.logging.Slf4j
import org.kohsuke.groovy.sandbox.GroovyInterceptor
import org.kohsuke.groovy.sandbox.GroovyValueFilter

@Slf4j
class GroovySandboxedEnvironment extends GroovyValueFilter {
    StringWriter sw = new StringWriter()
    PrintWriter out = new PrintWriter(sw)

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onMethodCall(GroovyInterceptor.Invoker invoker, Object receiver, String method, Object... args) throws Throwable {
        out.println "onMethodCall $receiver, ${receiver.getClass().name},  $method"
        preventUnknownPackages(receiverAsClass(receiver))
        preventClassAccess(receiverAsClass(receiver))
        preventMethodCall(receiverAsClass(receiver), method, args)
        preventURLProtocols(receiver, method, args)
        return super.onMethodCall(invoker, receiver, method, args)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onStaticCall(GroovyInterceptor.Invoker invoker, Class receiver, String method, Object... args) throws Throwable {
        out.println "onStaticCall $receiver, ${receiver.getClass().name},  $method"
        preventUnknownPackages(receiverAsClass(receiver))
        preventClassAccess(receiver)
        preventUnknownPackages(receiverAsClass(receiver))
        return super.onStaticCall(invoker, receiver, method, args)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onNewInstance(GroovyInterceptor.Invoker invoker, Class receiver, Object... args) throws Throwable {
        out.println "onNewInstance $receiver"
        preventUnknownPackages(receiverAsClass(receiver))
        preventClassAccess(receiverAsClass(receiver))
        return super.onNewInstance(invoker, receiver, args)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onGetProperty(GroovyInterceptor.Invoker invoker, Object receiver, String property) throws Throwable {
        out.println "onGetProperty $receiver, ${receiver.getClass().name},  $property"
        preventUnknownPackages(receiverAsClass(receiver))
        preventPropertyAccess(receiverAsClass(receiver), property)
        return super.onGetProperty(invoker, receiver, property)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onSetProperty(GroovyInterceptor.Invoker invoker, Object receiver, String property, Object value) throws Throwable {
        out.println "onSetProperty $receiver, ${receiver.getClass().name},  $property, $value"
        preventUnknownPackages(receiverAsClass(receiver))
        preventPropertyAccess(receiverAsClass(receiver), property)
        return super.onSetProperty(invoker, receiver, property, value)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onGetAttribute(GroovyInterceptor.Invoker invoker, Object receiver, String attribute) throws Throwable {
        println "onGetAttribute $receiver, ${receiver.getClass().name},  $attribute"
        preventUnknownPackages(receiverAsClass(receiver))
        return super.onGetAttribute(invoker, receiver, attribute)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onSetAttribute(GroovyInterceptor.Invoker invoker, Object receiver, String attribute, Object value) throws Throwable {
        out.println "onSetAttribute $receiver, ${receiver.getClass().name},  $attribute, $value"
        preventUnknownPackages(receiverAsClass(receiver))
        return super.onSetAttribute(invoker, receiver, attribute, value)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onGetArray(GroovyInterceptor.Invoker invoker, Object receiver, Object index) throws Throwable {
        out.println "onGetArray $receiver, ${receiver.getClass().name},  $index"
        preventUnknownPackages(receiverAsClass(receiver))
        return super.onGetArray(invoker, receiver, index)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Override
    Object onSetArray(GroovyInterceptor.Invoker invoker, Object receiver, Object index, Object value) throws Throwable {
        out.println "onSetArray $receiver, ${receiver.getClass().name},  $index, $value"
        preventUnknownPackages(receiverAsClass(receiver))
        return super.onSetArray(invoker, receiver, index, value)
    }

    private static void preventClassAccess(Class receiver) {
        if (receiver in [Runtime, System, File]) {
            throw new SecurityException("No access to ${receiver.name}")
        }
    }

    private static void preventMethodCall(Class receiver, String method, Object... args) {
        if (receiver == String && method in ['execute']) {
            throw doNotCall(receiver, method, args)
        }
        if (method in ['getResource', 'getResourceAsString']) {
            throw doNotCall(receiver, method, args)
        }
    }

    private static void preventURLProtocols(receiver, String method, Object... args) {
        if (receiver instanceof CharSequence && method in ['toURL', 'toURI']) {
            def u = receiver."$method"()
            if(u instanceof URI) {
                u = u.toURL()
            }
            if(!u.protocol.startsWith('http')) {
                throw new SecurityException("No access to URL/URI protocols not starting with http")
            }

        }

    }

    private void preventUnknownPackages(Class receiver) {
        out.println "$receiver.name, $receiver.package"
        if (!(['java', 'groovy', 'spock'].any { !receiver.package || receiver.package.name.startsWith(it) })) {
            throw new SecurityException("No access to ${receiver.name} since the class is not in 'java.', 'groovy.' or 'spock.' packages")
        }
    }

    private static void preventPropertyAccess(Class receiver, String property) {
        if (receiver in [Runtime] || property in ['metaClass']) {
            throw new SecurityException("No access to property ${receiver.name}.${property} ")
        }
    }


    private static SecurityException doNotCall(Class clazz, String method, Object... args) {
        return new SecurityException("Do not call ${clazz.name}.${method}(${args ? '...' : ''})")
    }

    private static Class receiverAsClass(receiver) {
        return receiver instanceof Class ? receiver : receiver.getClass()
    }

}
