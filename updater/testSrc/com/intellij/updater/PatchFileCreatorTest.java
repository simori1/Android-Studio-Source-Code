package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public abstract class PatchFileCreatorTest extends PatchTestCase {
  private File myFile;
  protected PatchSpec myPatchSpec;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myFile = getTempFile("patch.zip");
    myPatchSpec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath());
  }

  @Test
  public void testCreatingAndApplying() throws Exception {
    Patch patch = createPatch();

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testReverting() throws Exception {
    Patch patch = createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(patch));
    assertNothingHasChanged(patch, preparationResult, new HashMap<String, ValidationResult.Option>());
  }

  @Test
  public void testRevertedWhenFileToDeleteIsProcessLocked() throws Exception {
    if (!UtilsTest.mIsWindows) return;

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);


    RandomAccessFile raf = new RandomAccessFile(new File(myOlderDir, "bin/idea.bat"),"rw");
    try {
      // Lock the file. FileLock is not good here, because we need to prevent deletion.
      int b = raf.read();
      raf.seek(0);
      raf.write(b);

      PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

      Map<String, Long> original = patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);

      File backup = getTempFile("backup");
      PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), backup, TEST_UI);

      assertEquals(original, patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI));
    }
    finally {
      raf.close();
    }
  }

  @Test
  public void testApplyingWithAbsentFileToDelete() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testApplyingWithAbsentOptionalFile() throws Exception {
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new content".getBytes());

    myPatchSpec.setOptionalFiles(Collections.singletonList("bin/idea.bat"));
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertTrue(preparationResult.validationResults.isEmpty());
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testRevertingWithAbsentFileToDelete() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(patch));
    assertNothingHasChanged(patch, preparationResult, new HashMap<String, ValidationResult.Option>());
  }

  @Test
  public void testApplyingWithoutCriticalFiles() throws Exception {
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

    assertTrue(PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), TEST_UI));
  }

  @Test
  public void testApplyingWithCriticalFiles() throws Exception {
    myPatchSpec.setCriticalFiles(Arrays.asList("lib/annotations.jar"));
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

    assertTrue(PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), TEST_UI));
    assertAppliedCorrectly();
  }

  @Test
  public void testApplyingWithCaseChangedNames() throws Exception {
    FileUtil.rename(new File(myOlderDir, "Readme.txt"),
                    new File(myOlderDir, "README.txt"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenDirectoryBecomesFile() throws Exception {
    File file = new File(myOlderDir, "Readme.txt");
    file.delete();
    file.mkdirs();

    new File(file, "subFile.txt").createNewFile();
    new File(file, "subDir").mkdir();
    new File(file, "subDir/subFile.txt").createNewFile();

    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"),
                  new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenFileBecomesDirectory() throws Exception {
    File file = new File(myOlderDir, "bin");
    assertTrue(FileUtil.delete(file));
    file.createNewFile();

    FileUtil.copy(new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"),
                  new File(myOlderDir, "lib/boot.jar"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testConsideringOptions() throws Exception {
    Patch patch = createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    Map<String, ValidationResult.Option> options = new HashMap<String, ValidationResult.Option>();
    for (PatchAction each : preparationResult.patch.getActions()) {
      options.put(each.getPath(), ValidationResult.Option.IGNORE);
    }

    assertNothingHasChanged(patch, preparationResult, options);
  }

  @Test
  public void testApplyWhenCommonFileChanges() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.copy(new File(myOlderDir, "/lib/bootstrap.jar"),
                  new File(myOlderDir, "/lib/boot.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertTrue(preparationResult.validationResults.isEmpty());
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenCommonFileChangesStrict() throws Exception {
    myPatchSpec.setStrict(true);
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.copy(new File(myOlderDir, "/lib/bootstrap.jar"),
                  new File(myOlderDir, "/lib/boot.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertEquals(1, preparationResult.validationResults.size());
    assertEquals(new ValidationResult(ValidationResult.Kind.ERROR,
                                      "lib/boot.jar",
                                      ValidationResult.Action.VALIDATE,
                                      ValidationResult.MODIFIED_MESSAGE,
                                      ValidationResult.Option.NONE), preparationResult.validationResults.get(0));
  }

  @Test
  public void testApplyWhenNewFileExists() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.writeToFile(new File(myOlderDir, "newfile.txt"),"hello");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertTrue(preparationResult.validationResults.isEmpty());
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenNewFileExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.writeToFile(new File(myOlderDir, "newfile.txt"),"hello");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertEquals(1, preparationResult.validationResults.size());
    assertEquals(new ValidationResult(ValidationResult.Kind.CONFLICT,
                                      "newfile.txt",
                                      ValidationResult.Action.VALIDATE,
                                      "Unknown",
                                      ValidationResult.Option.DELETE), preparationResult.validationResults.get(0));
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  protected Patch createPatch() throws IOException, OperationCancelledException {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    assertTrue(myFile.exists());
    return patch;
  }

  private void assertNothingHasChanged(Patch patch, PatchFileCreator.PreparationResult preparationResult, Map<String, ValidationResult.Option> options)
    throws Exception {
    Map<String, Long> before = patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);
    PatchFileCreator.apply(preparationResult, options, TEST_UI);
    Map<String, Long> after = patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);

    DiffCalculator.Result diff = DiffCalculator.calculate(before, after);
    assertTrue(diff.filesToCreate.isEmpty());
    assertTrue(diff.filesToDelete.isEmpty());
    assertTrue(diff.filesToUpdate.isEmpty());
  }

  private void assertAppliedAndRevertedCorrectly(Patch patch, PatchFileCreator.PreparationResult preparationResult)
    throws IOException, OperationCancelledException {

    Map<String, Long> original = patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);

    File backup = getTempFile("backup");

    for (ValidationResult each : preparationResult.validationResults) {
      assertTrue(each.toString(), each.kind != ValidationResult.Kind.ERROR);
    }

    List<PatchAction> appliedActions =
      PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), backup, TEST_UI).appliedActions;
    assertAppliedCorrectly();

    assertFalse(original.equals(patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI)));

    PatchFileCreator.revert(preparationResult, appliedActions, backup, TEST_UI);

    assertEquals(original, patch.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI));
  }

  protected void assertAppliedCorrectly() throws IOException {
    File newFile = new File(myOlderDir, "newDir/newFile.txt");
    assertTrue(newFile.exists());
    assertEquals("hello", FileUtil.loadFile(newFile));

    File changedFile = new File(myOlderDir, "Readme.txt");
    assertTrue(changedFile.exists());
    assertEquals("hello", FileUtil.loadFile(changedFile));

    assertFalse(new File(myOlderDir, "bin/idea.bat").exists());

    // do not remove unchanged
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/Nls.class", 502);
    // add new
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/NewClass.class", 453);
    // update changed
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/Nullable.class", 546);
    // remove obsolete
    checkNoZipEntry("lib/annotations.jar", "org/jetbrains/annotations/TestOnly.class");

    // test for archives with only deleted files
    checkNoZipEntry("lib/bootstrap.jar", "com/intellij/ide/ClassloaderUtil.class");

    // packing directories too
    checkZipEntry("lib/annotations.jar", "org/", 0);
    checkZipEntry("lib/annotations.jar", "org/jetbrains/", 0);
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/intellij/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/intellij/ide/", 0);
  }

  private void checkZipEntry(String jar, String entryName, int expectedSize) throws IOException {
    ZipFile zipFile = new ZipFile(new File(myOlderDir, jar));
    try {
      ZipEntry entry = zipFile.getEntry(entryName);
      assertNotNull(entry);
      assertEquals(expectedSize, entry.getSize());
    }
    finally {
      zipFile.close();
    }
  }

  private void checkNoZipEntry(String jar, String entryName) throws IOException {
    ZipFile zipFile = new ZipFile(new File(myOlderDir, jar));
    try {
      assertNull(zipFile.getEntry(entryName));
    }
    finally {
      zipFile.close();
    }
  }

  private static class MyFailOnApplyPatchAction extends PatchAction {
    public MyFailOnApplyPatchAction(Patch patch) {
      super(patch, "_dummy_file_", -1);
    }

    @Override
    protected boolean isModified(File toFile) throws IOException {
      return false;
    }

    @Override
    protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected ValidationResult doValidate(File toFile) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void doApply(ZipFile patchFile, File toFile) throws IOException {
      throw new IOException("dummy exception");
    }

    @Override
    protected void doBackup(File toFile, File backupFile) throws IOException {
    }

    @Override
    protected void doRevert(File toFile, File backupFile) throws IOException {
    }
  }
}
