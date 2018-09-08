package org.tango.waltz.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.esrf.Tango.DevFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tango.client.database.DatabaseFactory;
import org.tango.utils.DevFailedUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 9/7/18
 */
public class DevicesTreeServlet extends HttpServlet{
    private final Logger logger = LoggerFactory.getLogger(DevicesTreeServlet.class);

    private final ConcurrentMap<String, TangoHost> tree = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] tango_hosts = req.getParameterValues("v");
        if(tango_hosts == null || tango_hosts.length == 0) {
            resp.setStatus(400);
            resp.setContentLength(0);
            return;
        }

        Iterator<String> it =
                new LinkedList<>(Arrays.asList(tango_hosts)).iterator();

        Collection result = new ArrayList();
        for(;it.hasNext();){
            String next = it.next();
            URI uri = null;
            try {
                uri = checkURISyntax(next);
            } catch (URISyntaxException e) {
                it.remove();
                continue;
            }

            fr.esrf.TangoApi.Database db;
            try {
                Object obj = DatabaseFactory.getDatabase(uri.getHost(), String.valueOf(uri.getPort()));
                Field fldDatabase = obj.getClass().getDeclaredField("database");
                fldDatabase.setAccessible(true);
                db = (fr.esrf.TangoApi.Database) fldDatabase.get(obj);
            } catch (DevFailed|IllegalAccessException|NoSuchFieldException devFailed) {
                logger.warn("Failed to get database for {}", next);
                it.remove();
                continue;
            }

            TangoHost tangoHost = processTangoHost(next, db);
            tree.putIfAbsent(next, tangoHost);
            result.add(tangoHost);

        }


        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");


        gson.toJson(result, resp.getWriter());
    }

    private TangoHost processTangoHost(String host, fr.esrf.TangoApi.Database db) {
        TangoHost result = new TangoHost(host, host);
        Collection data = new ArrayList();
        try {
            data.add(processAliases(host, db));
        } catch (DevFailed devFailed) {
            logger.warn("Failed to get aliases list for {} due to {}",host, DevFailedUtils.toString(devFailed));
        }
        data.addAll(processDomains(host, db));
        result.data = data.toArray();
        return result;
    }

    private Object processAliases(String host, fr.esrf.TangoApi.Database db) throws DevFailed {
        final String[] aliases = db.get_device_alias_list("*");
        return new TangoAliases(
                Arrays.stream(aliases).
                        map((String alias) ->
                        {
                            try {
                                return new TangoAlias(alias, db.get_device_from_alias(alias));
                            } catch (DevFailed devFailed) {
                                return null;
                            }
                        }).
                        filter(Objects::nonNull).toArray());
    }

    private static class TangoAliases {
        public final String value = "aliases";
        public String $css = "alias";
        public Object[] data;

        public TangoAliases(Object[] data) {
            this.data = data;
        }
    }

    private static class TangoDomain {
        public String value;
        public Object[] data;

        public TangoDomain(String value, Object[] data) {
            this.value = value;
            this.data = data;
        }
    }

    private Collection<Object> processDomains(String host, fr.esrf.TangoApi.Database db) {
        try {
            final String[] domains = db.get_device_domain("*");
            return Arrays.stream(domains).map((domain) -> {
                try {
                    String[] device_family = db.get_device_family(domain + "/*");
                    return new TangoDomain(domain, Arrays.stream(device_family).map((family) -> {
                        try {
                            String[] device_member = db.get_device_member(domain + "/" + family + "/*");
                            return new TangoDomain(family, Arrays.stream(device_member)
                                    .map(member -> new TangoMember(member, domain + "/" + family + "/" + member, host + "/" + domain + "/" + family + "/" + member)).toArray());

                        } catch (DevFailed devFailed) {
                            logger.warn("Failed to get member list for {} due to {}", host, DevFailedUtils.toString(devFailed));
                            return null;
                        }
                    }).filter(Objects::nonNull).toArray());

                } catch (DevFailed devFailed) {
                    logger.warn("Failed to get family list for {} due to {}",host, DevFailedUtils.toString(devFailed));
                    return null;
                }

            }).filter(Objects::nonNull).collect(Collectors.toList());
        } catch (DevFailed devFailed) {
            logger.warn("Failed to get domain list for {} due to {}",host, DevFailedUtils.toString(devFailed));
            return Collections.emptyList();
        }

    }

    private URI checkURISyntax(String next) throws URISyntaxException{
        try {
            return new URI("tango://" + next);
        } catch (URISyntaxException e) {
            logger.warn("Provided Tango host[{}] has wrong URI syntax. Skipping...", next);
            throw e;
        }
    }

    private static class TangoHost {
        public String id;
        public String $css = "tango_host";
        public String value;
        public Object[] data;

        public TangoHost(String id, String value) {
            this.id = id;
            this.value = value;
        }
    }

    private static class TangoAlias{
        public String value;
        public String $css = "member";
        public boolean isAlias = true;
        public String device_name;

        public TangoAlias(String value, String device_name) {
            this.value = value;
            this.device_name = device_name;
        }
    }

    private static class TangoMember{
        public String value;
        public String $css = "member";
        public boolean isMember = true;
        public String device_name;
        public String device_id;

        public TangoMember(String value, String device_name, String device_id) {
            this.value = value;
            this.device_name = device_name;
            this.device_id = device_id;
        }
    }
}
