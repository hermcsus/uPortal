/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.rendering;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.jasig.portal.cache.CacheKey;
import org.jasig.portal.portlet.rendering.IPortletExecutionManager;
import org.jasig.portal.xml.stream.FilteringXMLEventReader;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Initiates portlet rendering based each encountered {@link XMLPipelineConstants#CHANNEL} element in the
 * event stream
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public class PortletRenderingInitiationComponent implements StAXPipelineComponent {
    private IPortletExecutionManager portletExecutionManager;
    private StAXPipelineComponent parentComponent;
    
    public void setParentComponent(StAXPipelineComponent parentComponent) {
        this.parentComponent = parentComponent;
    }
    
    @Autowired
    public void setPortletExecutionManager(IPortletExecutionManager portletExecutionManager) {
        this.portletExecutionManager = portletExecutionManager;
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.rendering.PipelineComponent#getCacheKey(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public CacheKey getCacheKey(HttpServletRequest request, HttpServletResponse response) {
        //I don't think initiating rendering of portlets will change the stream at all
        return this.parentComponent.getCacheKey(request, response);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.rendering.PipelineComponent#getEventReader(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public CacheableEventReader<XMLEventReader, XMLEvent> getEventReader(HttpServletRequest request, HttpServletResponse response) {
        final CacheableEventReader<XMLEventReader, XMLEvent> cacheableEventReader = this.parentComponent.getEventReader(request, response);

        final CacheKey cacheKey = cacheableEventReader.getCacheKey();
        
        final XMLEventReader eventReader = cacheableEventReader.getEventReader();
        final PortletRenderingXMLEventReader filteredEventReader = new PortletRenderingXMLEventReader(request, response, eventReader);
        
        return new CacheableEventReaderImpl<XMLEventReader, XMLEvent>(cacheKey, filteredEventReader);
    }

    private class PortletRenderingXMLEventReader extends FilteringXMLEventReader {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        
        public PortletRenderingXMLEventReader(HttpServletRequest request, HttpServletResponse response, XMLEventReader reader) {
            super(reader);
            this.request = request;
            this.response = response;
        }

        @Override
        protected XMLEvent filterEvent(XMLEvent event, boolean peek) {
            //Don't start any rendering on a peek event
            if (peek) {
                return event;
            }
            
            if (event.isStartDocument()) {
                final StartElement startElement = event.asStartElement();
                
                final QName name = startElement.getName();
                if (XMLPipelineConstants.CHANNEL.equals(name)) {
                    final Attribute idAttribute = startElement.getAttributeByName(XMLPipelineConstants.ID_ATTR_NAME);
                    final String id = idAttribute.getValue();

                    if (!portletExecutionManager.isPortletRenderRequested(id, this.request, this.response)) {
                        portletExecutionManager.startPortletRender(id, this.request, this.response);
                    }
                }
            }
            
            return event;
        }
    }
}
