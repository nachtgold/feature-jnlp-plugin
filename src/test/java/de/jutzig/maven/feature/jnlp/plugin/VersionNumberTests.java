package de.jutzig.maven.feature.jnlp.plugin;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Der Testfall �berpr�ft, ob das Plugin die Versionnummern aus den Namen der
 * Libs und Features schneiden kann.
 */
@RunWith(value = Parameterized.class)
public class VersionNumberTests extends GenerateJNLPMojo {
	private static final List<String[]> testCases = new ArrayList<String[]>();
	private String expectedFilename;
	private String givenFilename;
	private String expectedJnlp;

	static {
		testCases.addAll(Arrays.asList(new String[][] {
				{ "javax.wsdl_1.6.2.v201012040545.jar", "javax.wsdl.jar", "javax.wsdl.jnlp" },
				{ "org.eclipse.core.databinding.property_1.4.100.v20120523-1955.jar", "org.eclipse.core.databinding.property.jar",
						"org.eclipse.core.databinding.property.jnlp" },
				{ "org.junit_4.10.0.v4_10_0_v20120426-0900.jar", "org.junit.jar", "org.junit.jnlp" } }));
	}

	@Parameters
	public static List<String[]> testingData() {
		return testCases;
	}

	public VersionNumberTests(String givenFilename, String expectedFilename, String expectedJnlp) {
		this.expectedFilename = expectedFilename;
		this.givenFilename = givenFilename;
		this.expectedJnlp = expectedJnlp;
	}

	@Test
	public void testSimpleFilename() {
		assertEquals(expectedFilename, removeVersion(givenFilename));
	}

	@Test
	public void testPluginWithVersion() {
		assertEquals("plugins/abc_1.0.0.jar", getPluginPath("abc", "1.0.0"));
	}

	@Test
	public void testPluginWithoutVersion() {
		this.ressourceNamesWithoutVersion = true;
		assertEquals("plugins/abc.jar", getPluginPath("abc", "1.0.0"));
	}

	@Test
	public void testJnlpNaming() {
		this.ressourceNamesWithoutVersion = true;
		assertEquals(expectedJnlp, getJnlpFileName(givenFilename));
	}
}
