package com.redhat.foreman.cli;

import com.redhat.foreman.cli.exception.ForemanApiException;
import com.redhat.foreman.cli.model.Host;
import com.redhat.foreman.cli.model.Parameter;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by shebert on 17/01/17.
 */
public class CreateTest extends AbstractTest {

    @Test
    public void testCreate2Hosts() throws ForemanApiException {
        createFromFile("create.json");

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_LABEL");
        assertNotNull(parameter);
        assertEquals("Should be SCOTT TOM", "SCOTT TOM", parameter.getValue());
    }

    @Test
    public void testCreate2HostsCsv() throws ForemanApiException {
        createFromFile("create.csv", true);

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_LABEL");
        assertNotNull(parameter);
        assertEquals("Should be SCOTT TOM", "SCOTT TOM", parameter.getValue());
    }

    @Test
    public void testCreateWithTokens() throws ForemanApiException {
        createFromFile("create-with-tokens.json", getResourceAsFile("tokens1.properties").getAbsolutePath());

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be /tmp/scott", "/tmp/scott", parameter.getValue());

        checkHost = api.getHost("scott2.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be /tmp/scott2", "/tmp/scott2", parameter.getValue());
    }

    @Test
    public void testCreateWithTokensCsv() throws ForemanApiException {
        createFromFile("create-with-tokens.csv", getResourceAsFile("tokens1.properties").getAbsolutePath(), true);

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be /tmp/scott", "/tmp/scott", parameter.getValue());

        checkHost = api.getHost("scott2.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be /tmp/scott2", "/tmp/scott2", parameter.getValue());
    }

    @Test
    public void testMissingPropertiesFile() throws ForemanApiException {
        createFromFile("create-with-tokens.json", "/tmp/ffgsfsfsd/sdfasfsaf/DOESNOTEXIST.properties");

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be ${FSROOT}", "${FSROOT}", parameter.getValue());
        assertThat(systemOutRule.getLog(), containsString("Could load properties from /tmp/ffgsfsfsd/sdfasfsaf/DOESNOTEXIST.properties"));
    }

    @Test
    public void testMissingPropertiesFileCsv() throws ForemanApiException {
        createFromFile("create-with-tokens.csv", "/tmp/ffgsfsfsd/sdfasfsaf/DOESNOTEXIST.properties", true);

        Host checkHost = api.getHost("scott.localdomain");
        assertNotNull(checkHost);
        assertNotNull(checkHost.parameters);
        Parameter parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNotNull(parameter);
        assertEquals("Should be ${FSROOT}", "${FSROOT}", parameter.getValue());
        assertThat(systemOutRule.getLog(), containsString("Could load properties from /tmp/ffgsfsfsd/sdfasfsaf/DOESNOTEXIST.properties"));
    }

    @Test
    public void testCreateAndAddParam() throws ForemanApiException {
        createFromFile("create-with-tokens.json");

        Host checkHost = api.getHost("scott.localdomain");
        api.updateHostParameter(checkHost, new Parameter("SCOTT", "TOM"));
        Parameter p1 = api.getHostParameter(checkHost, "SCOTT");
        Host checkHost2 = api.getHost("scott.localdomain");
        Parameter p2 = checkHost2.getParameterValue("SCOTT");
        assertNotNull(p1);
        assertNotNull(p2);
        assertEquals("Should be TOM", p1.getValue(), p2.getValue());

        ListHosts listHosts = new ListHosts();
        listHosts.server = getUrl();
        listHosts.user = user;
        listHosts.password = password;
        listHosts.run();
        assertThat(systemOutRule.getLog(), containsString("Found 2 host"));
    }

    @Test
    public void testCreateAndAddParamCsv() throws ForemanApiException {
        createFromFile("create-with-tokens.csv", true);

        Host checkHost = api.getHost("scott.localdomain");
        api.updateHostParameter(checkHost, new Parameter("SCOTT", "TOM"));
        Parameter p1 = api.getHostParameter(checkHost, "SCOTT");
        Host checkHost2 = api.getHost("scott.localdomain");
        Parameter p2 = checkHost2.getParameterValue("SCOTT");
        assertNotNull(p1);
        assertNotNull(p2);
        assertEquals("Should be TOM", p1.getValue(), p2.getValue());

        ListHosts listHosts = new ListHosts();
        listHosts.server = getUrl();
        listHosts.user = user;
        listHosts.password = password;
        listHosts.run();
        assertThat(systemOutRule.getLog(), containsString("Found 2 host"));
    }

    @Test
    public void testCreateDuplicateNames() throws ForemanApiException {
        exception.expect(RuntimeException.class);
        exception.expectMessage("Host scott.localdomain already exists");
        
        createFromFile("create-same-name.json");
    }

    @Test
    public void testCreateDuplicateNamesCsv() throws ForemanApiException {
        exception.expect(RuntimeException.class);
        exception.expectMessage("Host scott.localdomain already exists");

        createFromFile("create-same-name.csv", true);
    }

    @Test
    public void testCreateMissingInfo() throws ForemanApiException {
        exception.expect(RuntimeException.class);
        
        createFromFile("create-missing-name.json");
    }

    @Test
    public void testCreateMissingInfoCsv() throws ForemanApiException {
        exception.expect(RuntimeException.class);

        createFromFile("create-missing-name.csv", true);
    }

    @Test
    public void testCreateMissingParams() throws ForemanApiException {
        createFromFile("create-missing-parameters.json");

        Host checkHost = api.getHost("scott.localdomain");
        Parameter parameter = checkHost.getParameterValue("JENKINS_LABEL");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_JAVA_PATH");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("RESERVED");
        assertNotNull(parameter);
        assertEquals("Should be false", parameter.getValue(), "false");
    }

    @Test
    public void testCreateMissingParamsCsv() throws ForemanApiException {
        createFromFile("create-missing-parameters.csv", true);

        Host checkHost = api.getHost("scott.localdomain");
        Parameter parameter = checkHost.getParameterValue("JENKINS_LABEL");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_JAVA_PATH");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("RESERVED");
        assertNotNull(parameter);
        assertEquals("Should be false", parameter.getValue(), "false");

        checkHost = api.getHost("scott2.localdomain");
        parameter = checkHost.getParameterValue("JENKINS_LABEL");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_REMOTEFS_ROOT");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("JENKINS_SLAVE_JAVA_PATH");
        assertNull(parameter);
        parameter = checkHost.getParameterValue("RESERVED");
        assertNotNull(parameter);
        assertEquals("Should be false", parameter.getValue(), "false");
    }
}