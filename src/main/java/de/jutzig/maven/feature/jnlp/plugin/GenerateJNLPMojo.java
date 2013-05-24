package de.jutzig.maven.feature.jnlp.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal generate-jnlp
 * @phase package
 */
public class GenerateJNLPMojo extends AbstractMojo {
	/**
	 * Supported packaging.
	 */
	private static final String ECLIPSE_FEATURE = "eclipse-feature";

	/**
	 * Location of the file.
	 * 
	 * @parameter expression= "${generate-jnlp.featureJar}" default-value=
	 *            "target/site/features/${project.artifactId}_${project.version}.jar"
	 * @required
	 */
	private File featureJar;

	/**
	 * the vendor as it appear in the JNLP
	 * 
	 * @parameter expression= "${project.organization.name}"
	 * @required
	 */
	private String vendor;

	/**
	 * the title as it appear in the JNLP
	 * 
	 * @parameter expression= "${project.name}"
	 * @required
	 */
	private String title;

	/**
	 * the vendor as it appear in the JNLP
	 * 
	 * @parameter expression= "${generate-jnlp.codebase}" default-value=
	 *            "${project.url}"
	 * @required
	 */
	private String codebase;

	/**
	 * @parameter default-value="${project.packaging}"
	 * @required
	 */
	private String packaging;

	/**
	 * Describes an ordered list of version ranges to use.
	 * 
	 * @parameter expression="${generate-jnlp.javaVersion}" default-value="1.6+"
	 * @required
	 */
	private String javaVersion;

	/**
	 * Marks the JNLP runnable alone.
	 * 
	 * @parameter expression="${generate-jnlp.isStandalone}"
	 *            default-value="false"
	 * @required
	 */
	private boolean isStandalone;

	@Override
	public void execute() throws MojoExecutionException {
		if (!ECLIPSE_FEATURE.equalsIgnoreCase(packaging)) {
			getLog().debug(MessageFormat.format("The packaging of current project is not {0} but {1}", ECLIPSE_FEATURE, packaging));
			return;
		}

		Document feature = parseFeature(featureJar);
		Document jnlpDoc = createJNLP(feature);
		writeJNLP(featureJar, jnlpDoc);
	}

	private void writeJNLP(File featureJar, Document jnlpDoc) throws TransformerFactoryConfigurationError, MojoExecutionException {

		File jnlp = new File(featureJar.getParentFile(), featureJar.getName().substring(0, featureJar.getName().length() - 3) + "jnlp");

		Writer out = null;
		try {

			TransformerFactory tFactory = TransformerFactory.newInstance();
			tFactory.setAttribute("indent-number", 3);

			Transformer transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(jnlpDoc);
			out = new OutputStreamWriter(new FileOutputStream(jnlp));
			StreamResult result = new StreamResult(out);
			transformer.transform(source, result);
		} catch (IOException e) {
			throw new MojoExecutionException("Error creating file " + jnlp, e);
		} catch (TransformerConfigurationException e) {
			throw new MojoExecutionException("Transformation Configuration Error", e);
		} catch (TransformerException e) {
			throw new MojoExecutionException("Transformation Error" + jnlp, e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	private Document createJNLP(Document feature) throws MojoExecutionException {
		try {
			Element root = feature.getDocumentElement();
			Document jnlp = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			jnlp.setXmlStandalone(isStandalone);

			Element jnlpElement = jnlp.createElement("jnlp");
			jnlp.appendChild(jnlpElement);
			jnlpElement.setAttribute("spec", "1.0+");
			jnlpElement.setAttribute("codebase", codebase);

			Element information = jnlp.createElement("information");
			jnlpElement.appendChild(information);

			Element title = jnlp.createElement("title");
			title.setTextContent(this.title);
			information.appendChild(title);

			Element vendor = jnlp.createElement("vendor");
			vendor.setTextContent(this.vendor);
			information.appendChild(vendor);

			Element offline = jnlp.createElement("offline-allowed");
			information.appendChild(offline);

			Element security = jnlp.createElement("security");
			jnlpElement.appendChild(security);

			Element permissions = jnlp.createElement("all-permissions");
			security.appendChild(permissions);

			Element component = jnlp.createElement("component-desc");
			jnlpElement.appendChild(component);

			Element resources = jnlp.createElement("resources");
			jnlpElement.appendChild(resources);

			Element j2se = jnlp.createElement("j2se");
			resources.appendChild(j2se);
			j2se.setAttribute("version", javaVersion);

			Element jarResources = jnlp.createElement("resources");
			jnlpElement.appendChild(jarResources);

			parseIncludedFeatures(root, jnlp, jarResources);

			NodeList plugins = root.getElementsByTagName("plugin");
			int size = plugins.getLength();

			Map<String, Element> platformSpecificResources = new HashMap<String, Element>();

			for (int i = 0; i < size; i++) {
				Element plugin = (Element) plugins.item(i);
				String arch = plugin.getAttribute("arch");
				String os = plugin.getAttribute("os");
				String ws = plugin.getAttribute("ws");

				// if an arch is set, we must add several alternatives names for
				// the same thing :-(
				if (arch.equals("x86_64")) {
					createResourceElement(plugin, getPlatformResourceGroup(platformSpecificResources, plugin, jnlp, jnlpElement, "x86_64"),
							jnlp, jnlpElement);
					createResourceElement(plugin, getPlatformResourceGroup(platformSpecificResources, plugin, jnlp, jnlpElement, "amd64"),
							jnlp, jnlpElement);

				} else if (arch.equals("x86")) {
					createResourceElement(plugin, getPlatformResourceGroup(platformSpecificResources, plugin, jnlp, jnlpElement, "x86"),
							jnlp, jnlpElement);
					createResourceElement(plugin, getPlatformResourceGroup(platformSpecificResources, plugin, jnlp, jnlpElement, "i386"),
							jnlp, jnlpElement);
					createResourceElement(plugin, getPlatformResourceGroup(platformSpecificResources, plugin, jnlp, jnlpElement, "i686"),
							jnlp, jnlpElement);
				} else
					createResourceElement(plugin, jarResources, jnlp, jnlpElement);
			}

			return jnlp;

		} catch (DOMException e) {
			throw new MojoExecutionException("Error processing DOM", e);
		} catch (ParserConfigurationException e) {
			throw new MojoExecutionException("Parser Configuration Error", e);
		}
	}

	private Element getPlatformResourceGroup(Map<String, Element> platformSpecificResources, Element plugin, Document jnlp, Element root,
			String arch) {
		String os = plugin.getAttribute("os");

		String key = os + arch;
		Element resourceGroup = platformSpecificResources.get(key);
		if (resourceGroup == null) {
			resourceGroup = jnlp.createElement("resources");
			try {
				root.appendChild(resourceGroup);
			} catch (DOMException e) {
				System.out.println("Error processing DOM");
			}
			platformSpecificResources.put(key, resourceGroup);

			if (os.contains("win"))
				resourceGroup.setAttribute("os", "Windows");
			if (os.contains("mac"))
				resourceGroup.setAttribute("os", "Mac");
			if (os.contains("linux"))
				resourceGroup.setAttribute("os", "Linux");

			resourceGroup.setAttribute("arch", arch);
		}

		return resourceGroup;
	}

	/**
	 * Generates JNLP files for included features (&lt;includes&gt; tag).
	 * 
	 * @param currentEclipseFeature
	 * @param targetJnlp
	 * @param targetJnlpResourcesGroup
	 * @throws MojoExecutionException
	 * @throws TransformerFactoryConfigurationError
	 */
	private void parseIncludedFeatures(Element currentEclipseFeature, Document targetJnlp, Element targetJnlpResourcesGroup)
			throws MojoExecutionException, TransformerFactoryConfigurationError {
		NodeList features = currentEclipseFeature.getElementsByTagName("includes");
		for (int i = 0; i < features.getLength(); i++) {
			Element plugin = (Element) features.item(i);
			String includedFeature = plugin.getAttribute("id");
			String includedVersion = plugin.getAttribute("version");
			File includedEclipseFeature = new File(featureJar.getParent(), includedFeature + "_" + includedVersion + ".jar");

			if (includedEclipseFeature.exists()) {
				Document feature = parseFeature(includedEclipseFeature);
				Document jnlp = createJNLP(feature);
				writeJNLP(includedEclipseFeature, jnlp);

				Element extensionResources = targetJnlp.createElement("extension");
				String featureName = includedEclipseFeature.getName();
				extensionResources.setAttribute("href", "features/" + featureName.substring(0, featureName.length() - 4) + ".jnlp");
				try {
					targetJnlpResourcesGroup.appendChild(extensionResources);
				} catch (DOMException e) {
					System.out.println("Error processing DOM");
				}

			} else {
				getLog().warn("Unresolved Include: " + includedEclipseFeature.getAbsolutePath());
			}
		}
	}

	private void createResourceElement(Element plugin, Element resource, Document jnlp, Node jnlpElement) {
		String version = plugin.getAttribute("version");
		if (version == null || version.length() == 0 || version.equals("0.0.0"))
			// this plugin is not included for some reason. We must skip
			return;

		Element jar = jnlp.createElement("jar");
		try {
			resource.appendChild(jar);
		} catch (DOMException e) {
			System.out.println("Error processing DOM");
		}
		jar.setAttribute("href", "plugins/" + plugin.getAttribute("id") + "_" + plugin.getAttribute("version") + ".jar");

	}

	private Document parseFeature(File featureJar) {
		ZipInputStream in = null;
		try {
			in = new ZipInputStream(new FileInputStream(featureJar));
			ZipEntry entry = null;
			while ((entry = in.getNextEntry()) != null) {
				if (entry.getName().equals("feature.xml"))
					break;
			}
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);

			Document document = factory.newDocumentBuilder().parse(in);
			return document;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return null;
	}
}
