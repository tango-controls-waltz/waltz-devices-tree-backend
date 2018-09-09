package org.tango.waltz.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.Database;
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

        String[] filters = req.getParameterValues("f");
        DeviceFilters filter = new DeviceFilters(filters);

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

            TangoHost tangoHost = processTangoHost(next, db, filter);
            tree.putIfAbsent(next, tangoHost);
            result.add(tangoHost);

        }


        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");


        gson.toJson(result, resp.getWriter());
    }

    private TangoHost processTangoHost(String host, Database db, DeviceFilters filter) {
        TangoHost result = new TangoHost(host, host);
        Collection data = new ArrayList();
        try {
            data.add(processAliases(host, db, filter));
        } catch (DevFailed devFailed) {
            logger.warn("Failed to get aliases list for {} due to {}",host, DevFailedUtils.toString(devFailed));
        }
        data.addAll(processDomains(host, db, filter));
        result.data = data.toArray();
        return result;
    }

    private Object processAliases(String host, Database db, DeviceFilters filter) throws DevFailed {
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
                        filter(Objects::nonNull)
                        .filter(filter::checkDevice)
                        .toArray());
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

    private Collection<Object> processDomains(String host, Database db, DeviceFilters filter) {
            final List<String> domains = filter.getDomains(host, db);
            return domains.stream().map((domain) -> {
                    List<String> device_family = filter.getFamilies(host,db, domain);
                    return new TangoDomain(domain, device_family.stream().map((family) -> {
                            List<String> device_member = filter.getMembers(host, db, domain, family);
                            return new TangoDomain(family, device_member.stream()
                                    .map(member -> new TangoMember(member, domain + "/" + family + "/" + member, host + "/" + domain + "/" + family + "/" + member)).toArray());

                    }).toArray());
            }).collect(Collectors.toList());
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

    static class TangoAlias{
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
