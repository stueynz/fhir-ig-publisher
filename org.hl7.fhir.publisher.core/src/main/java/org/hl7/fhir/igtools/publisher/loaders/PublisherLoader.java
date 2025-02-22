package org.hl7.fhir.igtools.publisher.loaders;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.convertors.loaders.loaderR5.ILoaderKnowledgeProviderR5;
import org.hl7.fhir.convertors.loaders.loaderR5.R2016MayToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R2ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R3ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R4ToR5Loader;
import org.hl7.fhir.convertors.loaders.loaderR5.R5ToR5Loader;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.igtools.publisher.SpecMapManager.SpecialPackageType;
import org.hl7.fhir.r5.context.IWorkerContext.IContextResourceLoader;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

import com.google.gson.JsonSyntaxException;

public class PublisherLoader extends LoaderUtils implements ILoaderKnowledgeProviderR5 {

  private IGKnowledgeProvider igpkp;

  public PublisherLoader(NpmPackage npm, SpecMapManager spm, String pathToSpec, IGKnowledgeProvider igpkp) {
    super(npm, spm, pathToSpec);
    this.igpkp = igpkp;
  }

  public IContextResourceLoader makeLoader() {
    // there's no penalty for listing resources that don't exist, so we just all the relevant possibilities for all versions 
    List<String> types = Utilities.strings("CodeSystem", "ValueSet", "ConceptMap", "NamingSystem",
                                   "StructureDefinition", "StructureMap", 
                                   "SearchParameter", "OperationDefinition", "CapabilityStatement", "Conformance",
                                   "Questionnaire", "ImplementationGuide",
                                   "Measure");
    if (VersionUtilities.isR2Ver(npm.fhirVersion())) {
      return new R2ToR5Loader(types, this);
    } else if (VersionUtilities.isR2BVer(npm.fhirVersion())) {
      return new R2016MayToR5Loader(types, this);
    } else if (VersionUtilities.isR3Ver(npm.fhirVersion())) {
      return new R3ToR5Loader(types, this);
    } else if (VersionUtilities.isR4Ver(npm.fhirVersion())) {
      return new R4ToR5Loader(types, this, npm.version());
    } else if (VersionUtilities.isR4BVer(npm.fhirVersion())) {
      return new R4ToR5Loader(types, this, npm.version());
    } else {
      return new R5ToR5Loader(types, this);
    }
  }
  
  @Override
  public String getResourcePath(Resource resource) {
   
    if (isCore()) {
      return getCorePath(resource);
    } else {
      if (pathToSpec == null || igpkp == null) {
        if (spm != null && spm.getSpecial() == SpecialPackageType.Simplifier) {
          if (resource instanceof CanonicalResource) {
            return spm.getPath(((CanonicalResource) resource).getUrl(), resource.getMeta().getSource(), resource.fhirType(), resource.getId());
          } else {
            return null;
          }
        } else {
          return null;
        }
      }
      return getIgPath(resource);
    }
  }

  private String getIgPath(Resource r) {
    if (r instanceof CanonicalResource) {
      String u = ((CanonicalResource) r).getUrl();
      if (u != null) {
        if (u.contains("|")) {
          u = u.substring(0, u.indexOf("|"));
        }
        String p = spm.getPath(u, r.getMeta().getSource(), r.fhirType(), r.getId());
        if (p == null) {
          throw new FHIRException("Internal error in IG "+npm.name()+"#"+npm.version()+" map: No identity found for "+u);
        }
        if (!r.hasId()) {
          r.setId(tail(u));
        }
        String path;
        if (Utilities.isAbsoluteUrl(p)) {
          path = igpkp.doReplacements(p, r, null, null);            
        } else {
          path = pathToSpec+"/"+ igpkp.doReplacements(p, r, null, null);
        }
        r.setWebPath(path);
        r.setUserData("webroot", pathToSpec);
        String v = ((CanonicalResource) r).getVersion();
        if (v != null) {
          u = u + "|" + v;
          p = spm.getPath(u, r.getMeta().getSource(), r.fhirType(), r.getId());
          if (p == null) {
            System.out.println("In IG "+npm.name()+"#"+npm.version()+" map: No identity found for "+u);
          } else {
            String vp = pathToSpec+"/"+ igpkp.doReplacements(p, r, null, null);
            r.setUserData("versionpath", vp);
          }
        }
        return path;
      } 
      
    }
    return null;
  }

  private boolean isCore() {
    return npm.isCore();
  }

  
  private String tail(String ref) {
    if  (ref.contains("/"))
      return ref.substring(ref.lastIndexOf("/")+1);
    else
      return ref;
  }

  @Override
  public ILoaderKnowledgeProviderR5 forNewPackage(NpmPackage npm) throws JsonSyntaxException, IOException {
    return new PublisherLoader(npm, SpecMapManager.fromPackage(npm), npm.getWebLocation(), igpkp);
  }

  @Override
  public String getWebRoot() {
    return pathToSpec;
  }

}
