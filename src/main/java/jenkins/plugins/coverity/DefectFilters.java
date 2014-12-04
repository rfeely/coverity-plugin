/*******************************************************************************
 * Copyright (c) 2011 Coverity, Inc

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Coverity, Inc - initial implementation and documentation
 *******************************************************************************/
package jenkins.plugins.coverity;

import com.coverity.ws.v6.*;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.BuildListener;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Responsible for filtering the full list of defects to determine if a build should fail or not. Filters are inclusive:
 * a defect needs to pass every filter in order to be included.
 */
public class DefectFilters {
    private List<String> classifications;
    private List<String> actions;
    private List<String> severities;
    private List<String> components;
    private List<String> checkers;
    private List<String> ignoredCheckers;
    private Date cutOffDate;

    @DataBoundConstructor
    public DefectFilters(List<String> actions, List<String> classifications, List<String> severities, List<String> components, List<String> checkers, String cutOffDate) throws Descriptor.FormException {
        this.classifications = Util.fixNull(classifications);
        this.actions = Util.fixNull(actions);
        this.severities = Util.fixNull(severities);
        this.components = Util.fixNull(components);
        this.checkers = Util.fixNull(checkers);
        this.ignoredCheckers = new ArrayList<String>();

        cutOffDate = Util.fixEmpty(cutOffDate);
        if(cutOffDate != null) {
            try {
                this.cutOffDate = new SimpleDateFormat("yyyy-MM-dd").parse(cutOffDate);
            } catch(ParseException e) {
                this.cutOffDate = null;
            }
        } else {
            this.cutOffDate = null;
        }
    }

    void invertCheckers(Set<String> allCheckers, List<String> allClassifications, List<String> allActions, List<String> allSeverities, List<String> allComponents) {
        if(classifications.isEmpty() && checkers.isEmpty() && actions.isEmpty() && components.isEmpty() && severities.isEmpty()) {
            ignoredCheckers = new ArrayList<String>();
            actions = allActions;
            severities = allSeverities;
            components = allComponents;
            classifications = allClassifications;
        } else {
            ignoredCheckers = new ArrayList<String>(allCheckers);
            ignoredCheckers.removeAll(checkers);
        }
    }

    /**
     * We want to do a check on the defect configuration so that no null values are going into the build.
     * There has been issues on upgrade where configurations are changed, and not updates thus causing builds to
     * fail because some configuaritons are null.
     * @return true if defect configurations are not null
     */
    public boolean checkConfig(){
        if(this.getCheckersList() == null || this.getClassifications() == null ||
                this.getActions() == null || this.getSeverities() == null || this.getComponents() == null){
            return false;
        }
        return true;
    }

    public boolean isClassificationSelected(String action) {
        return classifications.contains(action);
    }

    public List<String> getCheckersList(){
        return checkers;
    }

    public boolean isActionSelected(String action) {
        return actions.contains(action);
    }

    public boolean isSeveritySelected(String severity) {
        return severities.contains(severity);
    }

    public boolean isComponentSelected(String component) {
        return components.contains(component);
    }

    
    /*public boolean isCheckerSelected(String checker) {
        if(ignoredCheckers == null) {
            return false;
        }
        
        System.out.println("checker is " + checker + " ignoredCheckers " + ignoredCheckers);
        return !ignoredCheckers.contains(checker);
    } */
    
    /* This method is rewritten to use the checkers array, instead of ignoredCheckers.  This fixes the issue where the plugin might
    not know about all the checkers that are found by analysis (especially findbugs). */
    public boolean isCheckerSelected(String checker) {
    
        if(checkers == null) {
            return false;
        }
        
        return checkers.contains(checker);
        
    }
    

    public String getCutOffDate() {
        if(cutOffDate == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd").format(cutOffDate);
    }

    public List<String> getClassifications(){return classifications;}

    public List<String> getActions(){return actions;}

    public List<String> getSeverities(){return severities;}

    public List<ComponentIdDataObj> getComponents(){
        List<ComponentIdDataObj> componentIdDataList = new ArrayList<ComponentIdDataObj>();
        for(String comp : components){
            ComponentIdDataObj cIdDataObj = new ComponentIdDataObj();
            cIdDataObj.setName(comp);
            componentIdDataList.add(cIdDataObj);
        }
        return componentIdDataList;
    }

    public List<CheckerSubcategoryFilterSpecDataObj> getCheckers(BuildListener listener){
        List<CheckerSubcategoryFilterSpecDataObj> checkerSubFilterSpecDataObjList = new ArrayList<CheckerSubcategoryFilterSpecDataObj>();
        for(String check : checkers){
            if(check != null){
                CheckerSubcategoryFilterSpecDataObj checkerSubFilterSpecDataObj = new CheckerSubcategoryFilterSpecDataObj();
                checkerSubFilterSpecDataObj.setCheckerName(check);
                checkerSubFilterSpecDataObjList.add(checkerSubFilterSpecDataObj);
            }
        }
        return checkerSubFilterSpecDataObjList;
    }

    public List<String> getIgnoredChecker(){return ignoredCheckers;}

    public XMLGregorianCalendar getXMLCutOffDate(){
        GregorianCalendar calender = new GregorianCalendar();
        calender.setTime(cutOffDate);
        try{
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calender);
        }catch(Exception e){

        }
        return null;
    }

    public boolean matches(MergedDefectDataObj defect, BuildListener listener) {
        return isActionSelected(defect.getAction()) &&
                isClassificationSelected(defect.getClassification()) &&
                isSeveritySelected(defect.getSeverity()) &&
                isComponentSelected(defect.getComponentName()) &&
                isCheckerSelected(defect.getCheckerName()) &&
                Arrays.asList("New", "Triaged", "Various").contains(defect.getStatus()) &&
                (cutOffDate == null || defect.getFirstDetected().toGregorianCalendar().getTime().after(cutOffDate));
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        DefectFilters that = (DefectFilters) o;

        if(actions != null ? !actions.equals(that.actions) : that.actions != null) return false;
        if(checkers != null ? !checkers.equals(that.checkers) : that.checkers != null) return false;
        if(classifications != null ? !classifications.equals(that.classifications) : that.classifications != null)
            return false;
        if(components != null ? !components.equals(that.components) : that.components != null) return false;
        if(cutOffDate != null ? !cutOffDate.equals(that.cutOffDate) : that.cutOffDate != null) return false;
        if(severities != null ? !severities.equals(that.severities) : that.severities != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classifications != null ? classifications.hashCode() : 0;
        result = 31 * result + (actions != null ? actions.hashCode() : 0);
        result = 31 * result + (severities != null ? severities.hashCode() : 0);
        result = 31 * result + (components != null ? components.hashCode() : 0);
        result = 31 * result + (checkers != null ? checkers.hashCode() : 0);
        result = 31 * result + (cutOffDate != null ? cutOffDate.hashCode() : 0);
        return result;
    }

    public CoverityPublisher.DescriptorImpl getPublisherDescriptor() {
        return Hudson.getInstance().getDescriptorByType(CoverityPublisher.DescriptorImpl.class);
    }

    /**
     * Set checkers is used when specific jenkins instances are set up with pre-exisiting configurations and we need to
     * reset the checkers. (Mainly because of STS)
     * @param cimInstance
     * @param streamId
     * @throws IOException
     * @throws CovRemoteServiceException_Exception
     */
    public void setCheckers(CIMInstance cimInstance,long streamId) throws IOException,CovRemoteServiceException_Exception{
        StreamDataObj stream = cimInstance.getStream(String.valueOf(streamId));
        String type = stream.getLanguage();

        if("MIXED".equals(type)) {
            type = "ALL";
        }

        try {
            String cs = getPublisherDescriptor().getCheckers(type);
            checkers = getPublisherDescriptor().split2List(cs);
        } catch(Exception e) {
            checkers = new LinkedList<String>();
        }
    }
}
