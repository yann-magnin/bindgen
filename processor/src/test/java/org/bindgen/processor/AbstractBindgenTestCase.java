package org.bindgen.processor;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;

import org.junit.Before;
import org.junit.BeforeClass;

public class AbstractBindgenTestCase {
	private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();
	private static final File outputBase = new File(new File(System.getProperty("java.io.tmpdir")), "bindgen");
	private static int testNumber = 0;
	private final HashMap<String, String> aptProperties = new HashMap<String, String>();
	private final File outputSub = new File(outputBase, String.valueOf(testNumber++));

	@BeforeClass
	public static void resetBase() {
		if (outputBase.exists() && !recursiveDelete(outputBase)) {
			System.err.println("Cannot delete " + outputBase);
		}
		outputBase.mkdirs();
	}

	@Before
	public void mkdirsOutputSub() {
		this.outputSub.mkdirs();
	}

	protected ClassLoader compile(String... files) throws CompilationErrorException, IOException {
		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
		StandardJavaFileManager fileManager = COMPILER.getStandardFileManager(diagnosticCollector, null, null);

		List<File> compilationUnits = new ArrayList<File>(files.length);
		for (String file : files) {
			compilationUnits.add(new File("src/test/template/" + file));
		}

		CompilationTask task = COMPILER.getTask(
			null,
			fileManager,
			diagnosticCollector,
			this.compileProps("-d", this.outputSub.getAbsolutePath()),
			null,
			fileManager.getJavaFileObjectsFromFiles(compilationUnits));

		task.setProcessors(Arrays.asList(new Processor[] { new org.bindgen.processor.Processor() }));

		task.call();

		fileManager.close();

		System.out.println(this.outputSub.getAbsolutePath());

		for (Diagnostic<? extends JavaFileObject> diag : diagnosticCollector.getDiagnostics()) {
			switch (diag.getKind()) {
				case ERROR:
					throw new CompilationErrorException(diagnosticCollector);
			}
		}

		return new URLClassLoader(new URL[] { this.outputSub.getAbsoluteFile().toURI().toURL() }, this.getClass().getClassLoader());
	}

	private List<String> compileProps(String... props) {
		List<String> result = new ArrayList<String>();
		result.addAll(Arrays.asList(props));

		for (Entry<String, String> prop : this.aptProperties.entrySet()) {
			result.add("-A" + prop.getKey() + "=" + prop.getValue());
		}
		return result;
	}

	protected void setBindingPathSuperClass(String qualifiedClassName) {
		this.aptProperties.put("bindingPathSuperClass", qualifiedClassName);
	}

	protected static String filePath(String qualifiedClassName) {
		return qualifiedClassName.replace(".", "/") + ".java";
	}

	protected static void assertPublic(Method method) {
		if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
			fail();
		}
	}

	protected static void assertProtected(Method method) {
		if ((method.getModifiers() & Modifier.PROTECTED) == 0) {
			fail();
		}
	}

	protected static void assertPackage(Method method) {
		if ((method.getModifiers() & Modifier.PUBLIC) > 0
			|| (method.getModifiers() & Modifier.PROTECTED) > 0
			|| (method.getModifiers() & Modifier.PRIVATE) > 0) {
			fail();
		}
	}

	public static void assertMethodDeclared(Class<?> bindingClass, String methodName) {
		try {
			assertNotNull(bindingClass.getDeclaredMethod(methodName));
		} catch (SecurityException e) {
			fail(e.toString());
		} catch (NoSuchMethodException e) {
			fail("Expected class " + bindingClass.getName() + " to declare method: " + methodName);
		}
	}

	public static void assertMethodNotDeclared(Class<?> bindingClass, String methodName) {
		try {
			bindingClass.getDeclaredMethod(methodName);
			fail("Expected class " + bindingClass.getName() + " to not declare method: " + methodName);
		} catch (SecurityException e) {
			fail(e.toString());
		} catch (NoSuchMethodException e) {
			// OK
		}
	}

	private static boolean recursiveDelete(File file) {
		File[] files = file.listFiles();
		if (files != null) {
			for (File each : files) {
				boolean worked = recursiveDelete(each);
				if (!worked) {
					return false;
				}
			}
		}
		return file.delete();
	}

}
