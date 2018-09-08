package org.tango.waltz.backend;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tango.utils.DevFailedUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 9/8/18
 */
public class DevicesFilter {
    private final Logger logger = LoggerFactory.getLogger(DevicesFilter.class);

    private String[] filters;
    private List<String> domains;
    private List<String> families;
    private List<String> members;

    public DevicesFilter(String[] filters) {
        if(filters == null || filters.length == 0)
            this.filters = new String[]{"*/*/*"};
        else this.filters = filters;


        this.domains = Arrays.stream(this.filters)
                .map(filter -> filter.split("/")[0] + "*").distinct().collect(Collectors.toList());
        this.families = Arrays.stream(this.filters)
                .map(filter -> filter.split("/")[1] + "*").distinct().collect(Collectors.toList());
        this.members = Arrays.stream(this.filters)
                .map(filter -> filter.split("/")[2] + "*").distinct().collect(Collectors.toList());
    }

    public List<String> getDomains(String host, Database db) {
        return domains.stream().map(domain -> {
            try {
                return db.get_device_domain(domain);
            } catch (DevFailed devFailed) {
                logger.warn("Failed to get domain list for {} due to {}",host, DevFailedUtils.toString(devFailed));
                return null;
            }
        }).flatMap(Arrays::stream).collect(Collectors.toList());
    }

    public List<String> getFamilies(String host, Database db, String domain) {
        return families.stream().map(family -> {
            try {
                return db.get_device_family(domain + "/" + family);
            } catch (DevFailed devFailed) {
                logger.warn("Failed to get family list for {} due to {}",host, DevFailedUtils.toString(devFailed));
                return null;
            }
        }).flatMap(Arrays::stream).collect(Collectors.toList());
    }

    public List<String> getMembers(String host, Database db, String domain, String family) {
        return members.stream().map(member -> {
            try {
                return db.get_device_member(domain + "/" + family + "/" + member);
            } catch (DevFailed devFailed) {
                logger.warn("Failed to get member list for {} due to {}",host, DevFailedUtils.toString(devFailed));
                return null;
            }
        }).flatMap(Arrays::stream).collect(Collectors.toList());
    }
}
