/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.credentialsbinding.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsParameterDefinition;
import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Functions;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.remoting.Future;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xmlunit.matchers.CompareMatcher;

public class UsernamePasswordB64BindingTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    private CredentialsStore store = null;

    @Test public void basics() throws Exception {
        String username = "bob";
        String password = "s3cr3t";
        String encodedWithColon = "Ym9iOnMzY3IzdA==";
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, "sample", username, password);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<Binding<?>>singletonList(new UsernamePasswordB64Binding("AUTH", c.getId()))));
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %AUTH% > auth.txt") : new Shell("echo $AUTH > auth.txt"));
        r.configRoundtrip(p);
        SecretBuildWrapper wrapper = p.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertNotNull(wrapper);
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertEquals(1, bindings.size());
        MultiBinding<?> binding = bindings.get(0);
        assertEquals(c.getId(), binding.getCredentialsId());
        assertEquals(UsernamePasswordB64Binding.class, binding.getClass());
        assertEquals("AUTH", ((UsernamePasswordB64Binding) binding).getVariable());
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        r.assertLogNotContains(password, b);
        assertEquals(encodedWithColon, b.getWorkspace().child("auth.txt").readToString().trim());
        assertEquals("[AUTH]", b.getSensitiveBuildVariables().toString());
    }

    @Test
    public void theSecretBuildWrapperTracksUsage() throws Exception {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
        Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());

        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "secret-id", "test credentials", "bob",
                                "secret");
        store.addCredentials(Domain.global(), credentials);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until first use", fingerprint, nullValue());

        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        FreeStyleProject job = r.createFreeStyleProject();
        // add a parameter
        job.addProperty(new ParametersDefinitionProperty(
                    new CredentialsParameterDefinition(
                              "SECRET",
                              "The secret",
                              "secret-id",
                              Credentials.class.getName(),
                              false
                        )));

        r.assertBuildStatusSuccess((Future) job.scheduleBuild2(0,
                        new ParametersAction(new CredentialsParameterValue("SECRET", "secret-id", "The secret", true))));

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("A job that does nothing does not use parameterized credentials", fingerprint, nullValue());

        page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        // check that the wrapper works as expected
        job.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<Binding<?>>singletonList(new UsernamePasswordB64Binding("AUTH", credentials.getId()))));

        r.assertBuildStatusSuccess((Future) job.scheduleBuild2(0, new ParametersAction(new CredentialsParameterValue("SECRET", "secret-id", "The secret", true))));

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(job);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(job.getLastBuild().getNumber()), is(true));

        page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat(page.getElementById("usage-missing"), nullValue());
        assertThat(page.getElementById("usage-present"), notNullValue());
        assertThat(page.getAnchorByText(job.getFullDisplayName()), notNullValue());

        // check the API
        WebResponse response = wc.goTo(
                  "credentials/store/system/domain/_/credentials/secret-id/api/xml?depth=1&xpath=*/fingerprint/usage",
                  "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), CompareMatcher.isSimilarTo("<usage>"
                  + "<name>"+ Util.xmlEscape(job.getFullName())+"</name>"
                  + "<ranges>"
                  + "<range>"
                  + "<end>"+(job.getLastBuild().getNumber()+1)+"</end>"
                  + "<start>" + job.getLastBuild().getNumber()+"</start>"
                  + "</range>"
                  + "</ranges>"
                  + "</usage>").ignoreWhitespace().ignoreComments());
    }
}
