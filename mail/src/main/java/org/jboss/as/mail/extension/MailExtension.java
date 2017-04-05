/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.mail.extension;

import static org.jboss.as.mail.extension.MailSubsystemModel.CUSTOM_SERVER_PATH;
import static org.jboss.as.mail.extension.MailSubsystemModel.SERVER_TYPE;
import static org.jboss.as.mail.extension.MailSubsystemModel.TLS;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;


/**
 * @author <a href="tomaz.cerar@gmail.com">Tomaz Cerar</a>
 * @since 7.1.0
 */
public class MailExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "mail";
    private static final String RESOURCE_NAME = MailExtension.class.getPackage().getName() + ".LocalDescriptions";
    static PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);
    static PathElement MAIL_SESSION_PATH = PathElement.pathElement(MailSubsystemModel.MAIL_SESSION);

    static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, MailExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MAIL_1_0.getUriString(), MailSubsystemParser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MAIL_1_1.getUriString(), MailSubsystemParser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MAIL_1_2.getUriString(), MailSubsystemParser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MAIL_2_0.getUriString(), MailSubsystemParser2_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MAIL_3_0.getUriString(), MailSubsystemParser3_0::new);
    }

    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(3, 0, 0);


    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_MODEL_VERSION);

        final ManagementResourceRegistration subsystemRegistration = subsystem.registerSubsystemModel(MailSubsystemResource.INSTANCE);
        subsystemRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        if (context.isRegisterTransformers()) {
            registerTransformers(subsystem);
        }
        subsystem.registerXMLElementWriter(new MailSubsystemParser3_0());
    }

    private void registerTransformers(SubsystemRegistration subsystem) {
        ChainedTransformationDescriptionBuilder chained = ResourceTransformationDescriptionBuilder.Factory.createChainedSubystemInstance(CURRENT_MODEL_VERSION);
        ModelVersion MODEL_VERSION_EAP64 = ModelVersion.create(1, 4, 0);
        ModelVersion MODEL_VERSION_EAP70 = ModelVersion.create(2, 0, 0);

        ResourceTransformationDescriptionBuilder builder70 = chained.createBuilder(CURRENT_MODEL_VERSION, MODEL_VERSION_EAP70);
        builder70.addChildResource(MAIL_SESSION_PATH).addChildResource(PathElement.pathElement(SERVER_TYPE))
                .getAttributeBuilder()
                    .addRejectCheck(RejectAttributeChecker.DEFINED, MailServerDefinition.CREDENTIAL_REFERENCE.getName())
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, MailServerDefinition.CREDENTIAL_REFERENCE.getName())
                .end()
                .addChildResource(CUSTOM_SERVER_PATH)
                    .getAttributeBuilder()
                        .addRejectCheck(RejectAttributeChecker.DEFINED, MailServerDefinition.CREDENTIAL_REFERENCE.getName())
                        .setDiscard(DiscardAttributeChecker.UNDEFINED, MailServerDefinition.CREDENTIAL_REFERENCE.getName());

        ResourceTransformationDescriptionBuilder builder64 = chained.createBuilder(MODEL_VERSION_EAP70, MODEL_VERSION_EAP64);
        ResourceTransformationDescriptionBuilder sessionBuilder = builder64.addChildResource(MAIL_SESSION_PATH)
            .addChildResource(PathElement.pathElement(SERVER_TYPE))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, TLS)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, TLS)
                .end();
        sessionBuilder.discardChildResource(CUSTOM_SERVER_PATH);

        chained.buildAndRegister(subsystem, new ModelVersion[] {MODEL_VERSION_EAP70,
                MODEL_VERSION_EAP64});
    }

}
