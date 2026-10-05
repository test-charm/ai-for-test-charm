package org.testcharm.extensions.dal;

import lombok.SneakyThrows;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.mockserver.model.*;
import org.testcharm.dal.DAL;
import org.testcharm.dal.extensions.basic.text.Methods;
import org.testcharm.dal.runtime.CollectionDALCollection;
import org.testcharm.dal.runtime.Extension;
import org.testcharm.dal.runtime.PropertyAccessor;
import org.testcharm.dal.runtime.ProxyObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MockserverRequestExtension implements Extension {

    @Override
    public void extend(DAL dal) {
        dal.getRuntimeContextBuilder().registerStaticMethodExtension(MockserverRequestExtension.class)
                .registerPropertyAccessor(ObjectBodyVerification.class, new PropertyAccessor<>() {
                    @Override
                    public Object getValue(ObjectBodyVerification instance, Object property) {
                        return instance.getValue(property);
                    }

                    @Override
                    public Set<Object> getPropertyNames(ObjectBodyVerification instance) {
                        return instance.keys();
                    }
                })
                .registerDALCollectionFactory(ListBodyVerification.class,
                        instance -> new CollectionDALCollection<>(instance.list()))
                .registerMetaProperty(BodyVerification.class, "headers", metaData -> metaData.data().instance().headers())
                .registerPropertyAccessor(Headers.class, new PropertyAccessor<>() {
                    @Override
                    public Object getValue(Headers instance, Object property) {
                        return singleOrList(instance.getValues(NottableString.string((String) property)));
                    }

                    @Override
                    public Set<Object> getPropertyNames(Headers instance) {
                        return instance.getMultimap().keys().stream().map(NottableString::getValue).collect(Collectors.toSet());
                    }
                });
        dal.getRuntimeContextBuilder().getConverter().addTypeConverter(PathVerification.class, String.class, PathVerification::path);
    }

    @SneakyThrows
    public static Object formData(HttpRequest request) {
        byte[] bytes = request.getBodyAsRawBytes();

        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);

        RequestContext context = new RequestContext() {
            @Override
            public String getCharacterEncoding() {
                return "UTF-8";
            }

            @Override
            public int getContentLength() {
                return bytes.length;
            }

            @Override
            public String getContentType() {
                return request.getHeader("Content-Type").get(0);
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
        return upload.parseRequest(context);
    }

    public static Object formUrlEncoded(HttpRequest request) {
        return URLEncodedUtils.parse(request.getBodyAsString(), StandardCharsets.UTF_8);
    }

    public static Map<?, Object> params(HttpRequest request) {
        List<Parameter> parameters = request.getQueryStringParameterList();
        if (parameters == null)
            return new EmptyParams();
        return parameters.stream().collect(Collectors.toMap(parameter -> parameter.getName().getValue(),
                parameter -> {
                    List<NottableString> values1 = parameter.getValues();
                    return singleOrList(values1);
                }));
    }

    private static Map<String, Object> headers(HttpRequest request) {
        return request.getHeaderList().stream().collect(Collectors.toMap(
                header -> header.getName().getValue(),
                header -> singleOrList(header.getValues())
        ));
    }

    public static Object json(Body body) {
        return Methods.json(body.getRawBytes());
    }

    public static Object GET(HttpRequest request, String url) {
        return verifyWithoutBody(request, "GET", url);
    }

    public static Object DELETE(HttpRequest request, String url) {
        return verifyWithoutBody(request, "DELETE", url);
    }

    private static Map<?, Object> verifyWithoutBody(HttpRequest request, String method, String url) {
        assertThat(request.getMethod().getValue()).isEqualTo(method);
        assertThat(request.getPath().getValue()).isEqualTo(url);
        return new HashMap<>() {{
            putAll(params(request));
            put("headers", headers(request));
        }};
    }

    private static Object singleOrList(Collection<NottableString> list) {
        List<String> values = list.stream().map(NottableString::getValue).toList();
        if (values.size() == 1)
            return values.get(0);
        return values;
    }

    public static class EmptyParams extends HashMap<Object, Object> {
    }

    public static PathVerification POST(HttpRequest request) {
        return new PathVerification(request, "POST");
    }

    public static PathVerification PUT(HttpRequest request) {
        return new PathVerification(request, "PUT");
    }

    public interface BodyVerification {
        HttpRequest request();

        default Map<String, Object> headers() {
            return MockserverRequestExtension.headers(request());
        }

        default Map<?, Object> params() {
            return MockserverRequestExtension.params(request());
        }
    }

    public interface ObjectBodyVerification extends BodyVerification {
        Object getValue(Object key);

        Set<Object> keys();
    }

    public interface ListBodyVerification extends BodyVerification {
        public List<Object> list();
    }

    private static class JsonArrayBodyVerification implements ListBodyVerification {
        private final HttpRequest request;
        private final List list;

        private JsonArrayBodyVerification(HttpRequest request, List list) {
            this.request = request;
            this.list = list;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public List<Object> list() {
            return list;
        }
    }

    private static class JsonObjectBodyVerification implements ObjectBodyVerification {
        private final HttpRequest request;
        private final Map object;

        public JsonObjectBodyVerification(HttpRequest request, Map object) {
            this.request = request;
            this.object = object;
        }

        @Override
        public Object getValue(Object key) {
            return object.get(key);
        }

        @Override
        public Set<Object> keys() {
            return object.keySet();
        }

        @Override
        public HttpRequest request() {
            return request;
        }
    }

    public static class PathVerification implements ProxyObject {
        private final HttpRequest request;

        public PathVerification(HttpRequest request, String method) {
            assertThat(request.getMethod().getValue()).isEqualTo(method);
            this.request = request;
        }

        @Override
        public BodyVerification getValue(Object url) {
            assertThat(request.getPath().getValue()).isEqualTo(url);

            List<String> header = request.getHeader("Content-Type");
            if (header != null) {
                if (header.get(0).equals("application/x-www-form-urlencoded") || header.get(0).equals("application/x-www-form-urlencoded; charset=UTF-8") || header.get(0).startsWith("multipart/form-data")) {
                    return new FormUrlEncodedObjectBodyVerification(request);
                } else {
                    Object json = Methods.json(request.getBodyAsString());
                    if (json instanceof Map) {
                        return new JsonObjectBodyVerification(request, (Map) json);
                    } else if (json instanceof List) {
                        return new JsonArrayBodyVerification(request, (List) json);
                    }
                }
            }

            throw new NotImplementedException();
        }

        public String path() {
            return request.getPath().getValue();
        }
    }

    public static class FormUrlEncodedObjectBodyVerification implements ObjectBodyVerification {
        private final HttpRequest request;
        private final Map body = new HashMap<>();

        public FormUrlEncodedObjectBodyVerification(HttpRequest request) {
            this.request = request;
            Map<String, List<String>> data = URLEncodedUtils.parse(request.getBodyAsString(), StandardCharsets.UTF_8)
                    .stream().collect(Collectors.groupingBy(NameValuePair::getName,
                            Collectors.mapping(NameValuePair::getValue, Collectors.toList())));
            data.forEach((key, value) -> body.put(key, value.size() == 1 ? value.get(0) : value));
        }

        @Override
        public Object getValue(Object key) {
            return body.get(key);
        }

        @Override
        public Set<Object> keys() {
            return body.keySet();
        }

        @Override
        public HttpRequest request() {
            return request;
        }
    }
}
