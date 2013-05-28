package de.jutzig.maven.feature.jnlp.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
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
import org.osgi.framework.Version;
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

	public static final String TYCHO_REPO_PATH = "target/site/";
	public static final String PLUGINS_WITH_VERSION_JAR = "plugins/{0}_{1}.jar";
	public static final String PLUGINS_WITHOUT_VERSION_JAR = "plugins/{0}.jar";

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

	/**
	 * @parameter default-value="false"
	 */
	protected boolean ressourceNamesWithoutVersion;

	/**
	 * @parameter default-value="false"
	 */
	protected boolean deleteFeatures;

	/**
	 * @parameter default-value="${basedir}"
	 */
	protected File baseDir;

	public void execute() throws MojoExecutionException {
		if (!ECLIPSE_FEATURE.equalsIgnoreCase(packaging)) {
			getLog().debug(MessageFormat.format("The packaging of current project is not {0} but {1}", ECLIPSE_FEATURE, packaging));
			return;
		}

		Document feature = parseFeature(featureJar);
		Document jnlpDoc = createJNLP(feature);
		writeJNLP(featureJar, jnlpDoc);

		if (deleteFeatures) {
			featureJar.deleteOnExit();
		}
	}

	private void writeJNLP(File featureJar, Document jnlpDoc) throws TransformerFactoryConfigurationError, MojoExecutionException {

		File jnlp = new File(featureJar.getParentFile(), getJnlpFileName(featureJar.getName()));

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
				if (arch != null && arch.length() > 0) {
					if ("x86_64".equals(arch)) {
						createResourceElement(plugin,
								getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, "x86_64", os, ws), jnlp, jnlpElement);
						createResourceElement(plugin,
								getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, "amd64", os, ws), jnlp, jnlpElement);

					} else if ("x86".equals(arch)) {
						createResourceElement(plugin,
								getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, "x86", os, ws), jnlp, jnlpElement);
						createResourceElement(plugin,
								getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, "i386", os, ws), jnlp, jnlpElement);
						createResourceElement(plugin,
								getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, "i686", os, ws), jnlp, jnlpElement);
					}

				} else if ((os != null && os.length() > 0) || (ws != null && ws.length() > 0)) {
					createResourceElement(plugin, getSpecificPlatformGroup(platformSpecificResources, jnlp, jnlpElement, arch, os, ws),
							jnlp, jnlpElement);

				} else {
					createResourceElement(plugin, jarResources, jnlp, jnlpElement);
				}
			}

			return jnlp;

		} catch (DOMException e) {
			throw new MojoExecutionException("Error processing DOM", e);
		} catch (ParserConfigurationException e) {
			throw new MojoExecutionException("Parser Configuration Error", e);
		}
	}

	private Element getSpecificPlatformGroup(Map<String, Element> platformSpecificGroups, Document jnlp, Element root, String arch,
			String os, String ws) {

		String archKey = "arch=";
		String osKey = ";os=";
		String wsKey = ";ws=";

		if (arch != null) {
			archKey = archKey + arch;
		}

		if (os != null) {
			osKey = osKey + os;
		}

		if (ws != null) {
			wsKey = wsKey + ws;
		}

		String key = archKey + osKey + wsKey;
		Element resourceGroup = platformSpecificGroups.get(key);

		if (resourceGroup == null) {
			resourceGroup = jnlp.createElement("resources");
			try {
				root.appendChild(resourceGroup);
			} catch (DOMException e) {
				System.out.println("Error processing DOM");
			}

			platformSpecificGroups.put(key, resourceGroup);

			if (os.contains("win")) {
				resourceGroup.setAttribute("os", "Windows");
			} else if (os.contains("mac")) {
				resourceGroup.setAttribute("os", "Mac");
			} else if (os.contains("linux")) {
				resourceGroup.setAttribute("os", "Linux");
			} else {
				resourceGroup.setAttribute("os", os);
			}

			resourceGroup.setAttribute("arch", arch);
			// resourceGroup.setAttribute("ws", ws);
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
				extensionResources.setAttribute("href", "features/" + getJnlpFileName(featureName));
				try {
					targetJnlpResourcesGroup.appendChild(extensionResources);
				} catch (DOMException e) {
					System.out.println("Error processing DOM");
				}

				if (deleteFeatures) {
					includedEclipseFeature.deleteOnExit();
				}

			} else {
				getLog().warn("Unresolved Include: " + includedEclipseFeature.getAbsolutePath());
			}
		}
	}

	private void createResourceElement(Element plugin, Element resource, Document jnlp, Node jnlpElement) throws MojoExecutionException {
		String version = plugin.getAttribute("version");
		if (version == null || version.length() == 0 || version.equals("0.0.0")) {
			// this plugin is not included for some reason. We must skip
			return;
		}

		String id = plugin.getAttribute("id");

		Element jar = jnlp.createElement("jar");
		try {
			resource.appendChild(jar);
		} catch (DOMException e) {
			System.out.println("Error processing DOM");
		}

		String bundleNameWithVersion = getPluginPath(false, id, version);
		String bundleName = getPluginPath(id, version);

		if (ressourceNamesWithoutVersion) {
			renamePlugin(id, bundleNameWithVersion, bundleName);
		}

		jar.setAttribute("href", bundleName);
	}

	private void renamePlugin(String id, String bundleNameWithVersion, String bundleName) throws MojoExecutionException {
		File bundle = new File(baseDir, TYCHO_REPO_PATH + bundleNameWithVersion);
		if (bundle.exists()) {

			File newBundleWithoutVersion = new File(baseDir, TYCHO_REPO_PATH + bundleName);
			boolean renamed = bundle.renameTo(newBundleWithoutVersion);

			if (!renamed) {
				String bsn = id;

				if (areThereMultipleBundles(bundle.getParentFile(), bsn)) {
					getLog().info("plugin " + bundleName + " exist in more than one version. All but the last will be removed!");
					removeAllButTheGreatest(bundle.getParentFile(), bsn);

					if (bundle.exists()) {
						bundle.renameTo(newBundleWithoutVersion);
					}

				} else {
					throw new MojoExecutionException(MessageFormat.format(
							"renaming {0} to {1} was not successfull (there are no other bundles with the same bsn)!",
							bundle.getAbsolutePath(), newBundleWithoutVersion));
				}
			}
		} else {
			getLog().debug(bundle.getAbsolutePath() + " could not renamed, because it didn't exist (maybe it was renamed already)!");
		}
	}

	private void removeAllButTheGreatest(File pluginsDir, final String bsn) throws MojoExecutionException {
		String[] bundlesWithSameBSN = pluginsDir.list(new BundleSymbolicNameFileFilter(bsn));
		Map<Version, File> versionedFiles = new HashMap<Version, File>();
		Version maxVersion = null;

		for (String fileName : bundlesWithSameBSN) {
			File file = new File(pluginsDir, fileName);

			try {
				JarFile jar = new JarFile(file);
				Attributes attributes = jar.getManifest().getMainAttributes();
				Version version = new Version(attributes.getValue("Bundle-Version"));
				jar.close();

				if (maxVersion == null || maxVersion.compareTo(version) < 0) {
					maxVersion = version;
				} else {
					File secondBundleWithSameVersion = versionedFiles.get(version);
					if (secondBundleWithSameVersion != null) {
						secondBundleWithSameVersion.delete();
					}
				}
				versionedFiles.put(version, file);

			} catch (IOException e) {
				throw new MojoExecutionException("one of the bundles with bsn " + bsn + " could no opened!", e);
			}
		}

		for (Iterator<Version> iterator = versionedFiles.keySet().iterator(); iterator.hasNext();) {
			Version version = (Version) iterator.next();
			if (!version.equals(maxVersion)) {
				versionedFiles.get(version).delete();
			}
		}
	}

	private boolean areThereMultipleBundles(File pluginsDir, final String bsn) {
		String[] bundlesWithSameBSN = pluginsDir.list(new BundleSymbolicNameFileFilter(bsn));
		return bundlesWithSameBSN.length > 1;
	}

	protected String getPluginPath(String id, String version) {
		return getPluginPath(ressourceNamesWithoutVersion, id, version);
	}

	protected String getPluginPath(boolean withoutVersion, String id, String version) {
		String naming = PLUGINS_WITH_VERSION_JAR;
		if (withoutVersion) {
			naming = PLUGINS_WITHOUT_VERSION_JAR;
		}
		return MessageFormat.format(naming, id, version);
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

	protected String removeVersion(String filename) {
		if (filename == null || filename.length() == 0) {
			return filename;
		}

		int underScore = filename.indexOf('_');
		if (underScore == -1) {
			return filename;
		}

		int extension = filename.lastIndexOf('.');

		return filename.substring(0, underScore).concat(filename.substring(extension));
	}

	protected String getJnlpFileName(String featureFilename) {
		if (ressourceNamesWithoutVersion) {
			featureFilename = removeVersion(featureFilename);
		}

		int extension = featureFilename.lastIndexOf('.');

		return featureFilename.substring(0, extension) + ".jnlp";
	}

	private final class BundleSymbolicNameFileFilter implements FilenameFilter {
		private final String bsn;

		private BundleSymbolicNameFileFilter(String bsn) {
			this.bsn = bsn;
		}

		public boolean accept(File dir, String name) {
			return name.startsWith(bsn);
		}
	}

}
