/*
 * The MIT License
 *
 * Copyright 2018 jglick.
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

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.DatatypeConverter;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;


public class UsernamePasswordB64Binding extends Binding<StandardUsernamePasswordCredentials> {

    @DataBoundConstructor public UsernamePasswordB64Binding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<StandardUsernamePasswordCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    @Override public Binding.SingleEnvironment bindSingle(@Nonnull Run<?,?> build,
                                                  @Nullable FilePath workspace,
                                                  @Nullable Launcher launcher,
                                                  @Nonnull TaskListener listener) throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials credentials = getCredentials(build);
        byte[] resultBytes = (credentials.getUsername() + ':' + credentials.getPassword().getPlainText()).getBytes("UTF-8");
        return new Binding.SingleEnvironment(DatatypeConverter.printBase64Binary(resultBytes));
    }

    @Symbol("usernameColonPasswordBase64")
    @Extension public static class DescriptorImpl extends BindingDescriptor<StandardUsernamePasswordCredentials> {

        @Override protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        @Override public String getDisplayName() {
            return Messages.UsernamePasswordB64Binding_username_and_password();
        }

        @Override public boolean requiresWorkspace() {
            return false;
        }
    }

}
