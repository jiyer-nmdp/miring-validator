/*

    MiringValidator  Semantic Validator for MIRING compliant HML
    Copyright (c) 2014-2015 National Marrow Donor Program (NMDP)

    This library is free software; you can redistribute it and/or modify it
    under the terms of the GNU Lesser General Public License as published
    by the Free Software Foundation; either version 3 of the License, or (at
    your option) any later version.

    This library is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
    License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this library;  if not, write to the Free Software Foundation,
    Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA.

    > http://www.gnu.org/licenses/lgpl.html

*/
package main.java.miringvalidator;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import main.java.miringvalidator.ValidationError.Severity;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ReportGenerator
{
    private static final Logger logger = LogManager.getLogger(ReportGenerator.class);
    
    /**
     * Generate a Miring Results Report
     *
     * @param validationErrors an array of ValidationError objects
     * @param root the root attribute on an HMLID node on the source XML.  If it exists, you should include it in the report
     * @param extension the extension attribute on an HMLID node on the source XML.  If it exists, you should include it in the report
     * @return a String containing MIRING Results Report
     */
    public static String generateReport(ValidationError[] validationErrors, String root, String extension)
    {
        validationErrors = combineSimilarErrors(validationErrors);
        try 
        {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
             
            //MIRINGREPORT ROOT
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("MiringReport");
            doc.appendChild(rootElement);
            
            //MIRINGCOMPLIANT ATTRIBUTE
            Attr compliantAttr = doc.createAttribute("miringCompliant");
            compliantAttr.setValue(
                (validationErrors == null)?"false"
                :(validationErrors.length==0)?"true"
                :(isMiringCompliant(validationErrors))?"true"
                :"false"
            );
            rootElement.setAttributeNode(compliantAttr);
            
            //HMLID element
            Element hmlidElement = doc.createElement("hmlid");
            if(root != null && root.length()>0)
            {
                Attr rootAttr = doc.createAttribute("root");
                rootAttr.setValue(root);
                hmlidElement.setAttributeNode(rootAttr);
            }
            if(extension != null && extension.length()>0)
            {
                Attr extAttr = doc.createAttribute("extension");
                extAttr.setValue(extension);
                hmlidElement.setAttributeNode(extAttr);
            }
            
            rootElement.appendChild(hmlidElement);
            
            //QUALITY SCORE
            //No errors = 1. 
            //No fatal errors = 2.
            //Some fatal errors = 3.            
            /*String quality = (validationErrors.length==0)?"1"
                    :(!containsFatalErrors(validationErrors))?"2"
                    :"3";
            
            Element qualityElement = doc.createElement("QualityScore");
            qualityElement.appendChild(doc.createTextNode(quality));
            rootElement.appendChild(qualityElement);*/
            
            //INVALIDMIRINGRESULT ELEMENTS
            if(validationErrors != null)
            {
                for(int i = 0; i < validationErrors.length; i++)
                {
                    rootElement.appendChild(generateValidationErrorNode(doc, validationErrors[i]));
                }
            }

            return(getStringFromDoc(doc));
        }
        catch (ParserConfigurationException pce) 
        {
            logger.error("Exception in Report Generator: " + pce);
        } 
        catch (Exception e) 
        {
            logger.error("Exception in Report Generator: " + e);
        }
        
        //Oops, something went wrong.
        logger.error("Error during Miring Validation Report Generation.");
        return null;
    }
    
    private static ValidationError[] combineSimilarErrors(ValidationError[] validationErrors)
    {
        //Many errors are very similar to eachother.  If they have the same error text, then we should combine them.  Hopefully they have distinct xpaths.
        List<ValidationError> newErrorList = new ArrayList<ValidationError>();
        
        for(int i = 0; i < validationErrors.length; i++)
        {
            ValidationError oldError = validationErrors[i];
            //Scan existing list for an error that is a close match.
            boolean foundMatch = false;
            for (ValidationError newError: newErrorList)
            {
                if(oldError.miringRule.equals(newError.miringRule)
                    && oldError.errorText.equals(newError.errorText))
                {
                    foundMatch = true;
                    //Add all the xpaths to the existing new error.
                    for (String xPath:oldError.xPaths)
                    {
                        if(!newError.xPaths.contains(xPath))
                        {
                            newError.addXPath(xPath);
                        }
                    }
                    Collections.sort(newError.xPaths);
                }
            }
            
            if(!foundMatch)
            {
                newErrorList.add(oldError);
            }
        }
        
        ValidationError[] results = new ValidationError[newErrorList.size()];
        for(int i = 0; i < newErrorList.size(); i++)
        {
            results[i]=newErrorList.get(i);
        }
        
        return results;
    }

    /**
     * Does this array contain zero fatal errors?  Any ValidationErrors with Severity=FATAL OR Severity=MIRING will return false.
     *
     * @param errors an array of ValidationError objects
     * @return is this report miring compliant?
     */
    private static boolean isMiringCompliant(ValidationError[] errors)
    {
        //Does this list contain any fatal errors?
        for(int i = 0; i < errors.length; i++)
        {
            if(errors[i].getSeverity()==Severity.FATAL  || errors[i].getSeverity()==Severity.MIRING)
            {
                return false;
            }
        }
        return true;
    }
    
    private static Element generateValidationErrorNode(Document doc, ValidationError validationError)
    {
        //Change a validation error into an XML Node to put in our report.
        Element invMiringElement = doc.createElement("InvalidMiringResult");
        
        //miringElementID
        Attr miringElementAttr = doc.createAttribute("miringRuleID");
        miringElementAttr.setValue(validationError.getMiringRule());
        invMiringElement.setAttributeNode(miringElementAttr);
        
        //severity
        Attr fatalAttr = doc.createAttribute("severity");
        fatalAttr.setValue(validationError.getSeverity()==Severity.FATAL?"fatal":
            validationError.getSeverity()==Severity.MIRING?"miring":
            validationError.getSeverity()==Severity.WARNING?"warning":
            validationError.getSeverity()==Severity.INFO?"info":
                "?");
        invMiringElement.setAttributeNode(fatalAttr);
        
        //description
        Element descriptionElement = doc.createElement("description");
        descriptionElement.appendChild(doc.createTextNode(validationError.getErrorText()));
        invMiringElement.appendChild(descriptionElement);
        
        //solution
        Element solutionElement = doc.createElement("solution");
        solutionElement.appendChild(doc.createTextNode(validationError.getSolutionText()));
        invMiringElement.appendChild(solutionElement);
        
        //xPath
        if(validationError.getXPaths() != null && validationError.getXPaths().size() > 0)
        {
            List<String> xPaths = validationError.getXPaths();
            for(int i = 0; i < xPaths.size(); i++)
            {
                Element xPathElement = doc.createElement("xpath");
                xPathElement.appendChild(doc.createTextNode(xPaths.get(i)));
                invMiringElement.appendChild(xPathElement);
            }
        }
        
        return invMiringElement;
    }
    
    private static String getStringFromDoc(Document doc)
    {
        //Generate an XML String from the Document object.
        String xmlString = null;
        try
        {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            //initialize StreamResult with File object to save to file
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);
            xmlString = result.getWriter().toString();
        }
        catch(Exception e)
        {
            logger.error("Error generating XML String" + e);
        }
        return xmlString;
    }
}
