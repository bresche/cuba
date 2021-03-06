/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.haulmont.cuba.web.sys.remoting;

import com.haulmont.cuba.core.global.RemoteException;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.core.sys.remoting.*;
import com.haulmont.cuba.core.sys.serialization.SerializationSupport;
import com.haulmont.cuba.security.global.ClientBasedSession;
import com.haulmont.cuba.security.global.UserSession;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.remoting.support.RemoteAccessor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * INTERNAL.
 * <p>
 * Bean of the web block that creates a proxy object to invoke middleware services directly
 * using {@link LocalServiceInvoker}.
 */
public class LocalServiceProxy extends RemoteAccessor implements FactoryBean<Object>, InitializingBean {

    private Object serviceProxy;
    private String serviceName;

    @Override
    public void afterPropertiesSet() {
        if (serviceName == null)
            throw new IllegalStateException("Property 'serviceName' is required");
        if (getServiceInterface() == null)
            throw new IllegalStateException("Property 'serviceInterface' is required");

        serviceProxy = Proxy.newProxyInstance(
                getBeanClassLoader(),
                new Class[]{getServiceInterface()},
                new LocalServiceInvocationHandler(serviceName)
        );
    }

    @Override
    public Object getObject() throws Exception {
        return serviceProxy;
    }

    @Override
    public Class<?> getObjectType() {
        return getServiceInterface();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        if (serviceName.startsWith("/"))
            this.serviceName = serviceName;
        else
            this.serviceName = "/" + serviceName;
    }

    private static class LocalServiceInvocationHandler implements InvocationHandler {

        private String serviceName;

        public LocalServiceInvocationHandler(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String connectionUrlList = AppContext.getProperty("cuba.connectionUrlList");
            if (connectionUrlList == null)
                throw new IllegalStateException("Property cuba.connectionUrlList not defined");

            String middlewareContextName = connectionUrlList.substring(connectionUrlList.lastIndexOf('/') + 1);
            String entryName = middlewareContextName + serviceName;
            LocalServiceInvoker invoker = LocalServiceDirectory.getInvoker(entryName);
            if (invoker == null) {
                LocalServiceAccessException exception = createServiceAccessException(middlewareContextName, entryName);
                throw exception;
            }

            Parameter[] parameters = method.getParameters();
            Class<?>[] parameterTypes = method.getParameterTypes();
            String[] parameterTypeNames = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                parameterTypeNames[i] = parameterTypes[i].getName();
            }

            byte[][] argumentsData;
            Object[] notSerializableArguments;
            if (args == null) {
                argumentsData = null;
                notSerializableArguments = null;
            } else {
                argumentsData = new byte[args.length][];
                notSerializableArguments = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    Parameter parameter = parameters[i];
                    Object arg = args[i];
                    if (canBypassSerialization(parameter)) {
                        notSerializableArguments[i] = args[i];
                        argumentsData[i] = null;
                    } else if (arg != null) {
                        argumentsData[i] = SerializationSupport.serialize(arg);
                    } else {
                        argumentsData[i] = null;
                    }
                }
            }

            SecurityContext securityContext = AppContext.getSecurityContext();
            UUID sessionId = securityContext == null ? null : securityContext.getSessionId();

            LocalServiceInvocation invocation = new LocalServiceInvocation(
                    method.getName(), parameterTypeNames, argumentsData, notSerializableArguments, sessionId);

            if (securityContext != null) {
                UserSession session = securityContext.getSession();

                if (session instanceof ClientBasedSession && ((ClientBasedSession) session).hasRequestScopedInfo()) {
                    invocation.setLocale(session.getLocale() != null ? session.getLocale().toLanguageTag() : null);
                    invocation.setTimeZone(session.getTimeZone());
                    invocation.setAddress(session.getAddress());
                    invocation.setClientInfo(session.getClientInfo());
                }
            }
            invocation.setResultBypassSerialization(canMethodResultBypassSerialization(method));

            LocalServiceInvocationResult result = invoker.invoke(invocation);
            AppContext.setSecurityContext(AppContext.getSecurityContext()); // to reset application name in LogMDC for the current thread

            // don't use SerializationUtils.deserialize() here to avoid ClassNotFoundException
            if (result.getException() != null) {
                Throwable t = (Throwable) SerializationSupport.deserialize(result.getException());
                if (t instanceof RemoteException) {
                    Exception exception = ((RemoteException) t).getFirstCauseException();
                    if (exception != null) // This is a checked exception declared in a service method
                        throw exception;
                }
                throw t;
            } else {
                Object data;
                if (result.getNotSerializableData() == null) {
                    data = SerializationSupport.deserialize(result.getData());
                } else {
                    data = result.getNotSerializableData();
                }
                return data;
            }
        }

        private LocalServiceAccessException createServiceAccessException(String middlewareContextName, String entryName) {
            Map<String, CoreBlockStatusDirectory.StatusInfo> registeredBlocks = CoreBlockStatusDirectory.getBlocks();
            CoreBlockStatusDirectory.StatusInfo mwStatus = registeredBlocks.get(middlewareContextName);
            if (mwStatus != null) {
                if (mwStatus.isSuccess()) {
                    return new LocalServiceAccessException(String.format(
                            "Unable to connect to middleware. Service %s is not registered in LocalServiceDirectory." +
                                    " Please check that service implementation class is annotated as @Service", entryName));

                } else {
                    Throwable cause = mwStatus.getException();
                    return new LocalServiceAccessException("Unable to connect to middleware. Middleware block '"
                            + middlewareContextName + "' failed to start. See exception cause for details.", cause);
                }
            } else if (registeredBlocks.size() > 0) {
                String appNames = registeredBlocks.values().stream()
                        .map(statusInfo -> "'" + statusInfo.getWebContextName() + "'")
                        .collect(Collectors.joining(", "));
                String exampleCoreName = registeredBlocks.values().iterator().next().getWebContextName();
                String message = String.format(
                        "Unable to connect to middleware block '%s'. Registered middleware blocks are: %s. Possible causes: \n"
                                + "'cuba.connectionUrlList' property value not ending with the actual web context name "
                                + "of the 'core' block, e.g. 'cuba.connectionUrlList = http://localhost:8080/%s',\n"
                                + "or incorrect value of the 'cuba.webContextName' property in the 'core' block.",
                        middlewareContextName, appNames, exampleCoreName);
                return new LocalServiceAccessException(message);
            } else {
                return new LocalServiceAccessException("Unable to connect to middleware block '" + middlewareContextName + "'. "
                        + "No middleware blocks are registered in the application server. Possible causes:\n"
                        + "'cuba.useLocalServiceInvocation' property enabled by mistake for unsupported deployment methods,\n"
                        + "or 'core' application missing from 'webapps' folder (in case of fast deployment).");
            }
        }

        private boolean canBypassSerialization(Parameter parameter) {
            return parameter.getAnnotation(BypassSerialization.class) != null;
        }

        private boolean canMethodResultBypassSerialization(Method method) {
            return method.getAnnotation(BypassSerialization.class) != null;
        }
    }
}