package org.lamport.tla.toolbox.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Date;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.lamport.tla.toolbox.Activator;
import org.lamport.tla.toolbox.job.NewTLAModuleCreationOperation;
import org.lamport.tla.toolbox.spec.Spec;
import org.lamport.tla.toolbox.spec.nature.PCalDetectingBuilder;
import org.lamport.tla.toolbox.spec.nature.TLANature;
import org.lamport.tla.toolbox.spec.nature.TLAParsingBuilder;
import org.lamport.tla.toolbox.spec.parser.IParseConstants;
import org.lamport.tla.toolbox.spec.parser.ParseResult;
import org.lamport.tla.toolbox.spec.parser.ParseResultBroadcaster;
import org.lamport.tla.toolbox.tool.ToolboxHandle;
import org.lamport.tla.toolbox.ui.preference.EditorPreferencePage;
import org.lamport.tla.toolbox.util.pref.IPreferenceConstants;
import org.lamport.tla.toolbox.util.pref.PreferenceStoreHelper;

import tla2sany.modanalyzer.SpecObj;
import tla2sany.semantic.DefStepNode;
import tla2sany.semantic.InstanceNode;
import tla2sany.semantic.LeafProofNode;
import tla2sany.semantic.LevelNode;
import tla2sany.semantic.ModuleNode;
import tla2sany.semantic.NonLeafProofNode;
import tla2sany.semantic.ProofNode;
import tla2sany.semantic.TheoremNode;
import tla2sany.semantic.UseOrHideNode;
import tla2sany.st.Location;
import util.UniqueString;

/**
 * A toolbox with resource related methods
 * @author Simon Zambrovski
 * @version $Id$
 */
public class ResourceHelper
{

    /**
     * 
     */
    private static final String PROJECT_DESCRIPTION_FILE = ".project";
    /**
     * 
     */
    private static final String TOOLBOX_DIRECTORY_SUFFIX = ".toolbox";

    /**
     * TLA extension
     */
    public static final String TLA_EXTENSION = "tla";

    /*
     * Constants used for displaying modification history.
     * It has the syntax:
     *   modificationHistory +
     *   (lastModified + date + modifiedBy +
     *      username + newline)*
     */
    public static String modificationHistory = StringHelper.newline + "\\* Modification History";

    public static String lastModified = StringHelper.newline + "\\* Last modified ";

    public static String modifiedBy = " by ";

    /**
     * Look up if a project exist and return true if so
     * @param name name of the project
     * @return
     */
    public static boolean peekProject(String name, String rootFilename)
    {
        String root = getParentDirName(rootFilename);
        if (root == null)
        {
            return false;
        }
        File projectDir = new File(root.concat("/").concat(name).concat(TOOLBOX_DIRECTORY_SUFFIX));
        return projectDir.exists();
    }

    /**
     * Retrieves a resource in the current project (the one of current loaded spec) 
     * @param name name of the module (no extension)
     * @return IResource if found or <code>null</code>
     */
    public static IResource getResourceByModuleName(String name)
    {
        return getResourceByName(getModuleFileName(name));
    }

    /**
     * Retrieves a resource in the current project (the one of current loaded spec) 
     * @param name name of the resource
     * @return IResource if found or <code>null</code>
     */
    public static IResource getResourceByName(String name)
    {
        Spec spec = Activator.getSpecManager().getSpecLoaded();
        if (spec != null)
        {
            IProject project = spec.getProject();
            return getLinkedFile(project, name, false);
        }
        return null;
    }

    /**
     * Retrieves the project handle
     * @param name name of the project
     * @return a project handle
     */
    public static IProject getProject(String name)
    {
        if (name == null)
        {
            return null;
        }
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IProject project = ws.getRoot().getProject(name);

        return project;
    }

    /**
     * Retrieves a project by name or creates a new project is <code>createMissing</code> is set to <code>true</code>
     * @param name name of the project, should not be null
     * @param rootFilename path to the root filename, should not be null
     * @param createMissing a boolean flag if a missing project should be created 
     * @return a working IProject instance or null if project not at place and createMissing was false
     * 
     * <br><b>Note:</b> If the project does not exist in workspace it will be created, based
     * on the specified name and the path of the rootFilename. <br>The location (directory) of 
     * the project will be constructed as 
     * the parent directory of the rootFile + specified name + ".toolbox". 
     * <br>Eg. calling <tt>getProject("for", "c:/bar/bar.tla")</tt>
     * will cause the creation of the project (iff this does not exist) with location
     * <tt>"c:/bar/foo.toolbox"</tt>
     * 
     */
    public static IProject getProject(String name, String rootFilename, boolean createMissing, boolean importExisting)
    {
        if (name == null)
        {
            return null;
        }

        IProgressMonitor monitor = new NullProgressMonitor();
        IProject project = getProject(name);

        // create a project
        if (!project.exists() && createMissing)
        {
            try
            {
                if (rootFilename == null)
                {
                    return null;
                }

                String parentDirectory = getParentDirName(rootFilename);

                Assert.isNotNull(parentDirectory);

                // parent directory could be determined

                IPath projectDescriptionPath = new Path(parentDirectory).removeTrailingSeparator().append(
                        name.concat(TOOLBOX_DIRECTORY_SUFFIX)).addTrailingSeparator();

                // create a new description for the given name
                IProjectDescription description;

                boolean performImport = importExisting
                        && projectDescriptionPath.append(PROJECT_DESCRIPTION_FILE).toFile().exists();

                // there exists a project description file
                if (performImport)
                {
                    description = project.getWorkspace().loadProjectDescription(
                            projectDescriptionPath.append(PROJECT_DESCRIPTION_FILE));

                    // check the natures and install if missing
                    String[] natureIds = description.getNatureIds();
                    boolean natureFound = false;
                    for (int i = 0; i < natureIds.length; i++)
                    {
                        if (TLANature.ID.equals(natureIds[i]))
                        {
                            natureFound = true;
                            break;
                        }
                    }
                    if (!natureFound)
                    {
                        String[] newNatureIds = new String[natureIds.length + 1];
                        System.arraycopy(natureIds, 0, newNatureIds, 0, natureIds.length);
                        newNatureIds[natureIds.length] = TLANature.ID;
                        description.setNatureIds(newNatureIds);
                    }

                    // check builders and install if missing
                    ICommand[] commands = description.getBuildSpec();
                    boolean tlaBuilderFound = false;
                    boolean pcalBuilderFound = false;
                    int numberOfBuildersToInstall = 2;

                    for (int i = 0; i < commands.length; ++i)
                    {
                        String builderName = commands[i].getBuilderName();
                        if (builderName.equals(TLAParsingBuilder.BUILDER_ID))
                        {
                            tlaBuilderFound = true;
                            numberOfBuildersToInstall--;
                        } else if (builderName.equals(PCalDetectingBuilder.BUILDER_ID))
                        {
                            pcalBuilderFound = true;
                            numberOfBuildersToInstall--;
                        }

                        if (tlaBuilderFound && pcalBuilderFound)
                        {
                            break;
                        }
                    }

                    if (numberOfBuildersToInstall > 0)
                    {
                        ICommand[] newCommands = new ICommand[commands.length + numberOfBuildersToInstall];
                        System.arraycopy(commands, 0, newCommands, 0, commands.length);

                        int position = commands.length;

                        if (!tlaBuilderFound)
                        {
                            ICommand command = description.newCommand();
                            command.setBuilderName(TLAParsingBuilder.BUILDER_ID);
                            newCommands[position] = command;
                            position++;
                        }
                        if (!pcalBuilderFound)
                        {
                            ICommand command = description.newCommand();
                            command.setBuilderName(PCalDetectingBuilder.BUILDER_ID);
                            newCommands[position] = command;
                        }
                    }
                } else
                {
                    // create new description
                    description = project.getWorkspace().newProjectDescription(name);
                    description.setLocation(projectDescriptionPath);

                    // set TLA+ feature
                    description.setNatureIds(new String[] { TLANature.ID });

                    // set TLA+ Parsing Builder
                    ICommand command = description.newCommand();
                    command.setBuilderName(TLAParsingBuilder.BUILDER_ID);
                    // set PCal detecting builder
                    ICommand command2 = description.newCommand();
                    command2.setBuilderName(PCalDetectingBuilder.BUILDER_ID);

                    // setup the builders
                    description.setBuildSpec(new ICommand[] { command, command2 });
                }

                // create the project
                project.create(description, monitor);
                // refresh
                project.refreshLocal(IResource.DEPTH_INFINITE, monitor);

                // open the project
                project.open(monitor);

                // relocate files (fix the links)
                if (performImport)
                {
                    relocateFiles(project, new Path(parentDirectory), new NullProgressMonitor());
                }

            } catch (CoreException e)
            {
                Activator.logError("Error creating the project " + name, e);
            }
        }

        return project;

    }

    /**
     * Retrieves a a resource from the project, creates a link if createNew is true and the file is not present 
     * TODO improve this, if the name is wrong
     * 
     * @param name full filename of the resource
     * @param project
     * @param createNew, a boolean flag indicating if the new link should be created if it does not exist
     */
    public static IFile getLinkedFile(IContainer project, String name, boolean createNew)
    {
        if (name == null || project == null)
        {
            return null;
        }
        IPath location = new Path(name);
        IFile file = project.getFile(new Path(location.lastSegment()));
        if (createNew)
        {
            if (!file.isLinked())
            {
                try
                {
                    file.createLink(location, IResource.NONE, new NullProgressMonitor());
                    return file;

                } catch (CoreException e)
                {
                    Activator.logError("Error creating resource link to " + name, e);
                }
            }
            if (file.exists())
            {
                return file;
            } else
            {
                return null;
            }
        }
        return file;
    }

    /**
     * On relocation, all linked files in the project become invalid. This method fixes this issue
     * @param project project containing links to the nen-existing files
     * @param newLocationParent the parent directory, where the files are located
     */
    public static void relocateFiles(IProject project, IPath newLocationParent, IProgressMonitor monitor)
    {
        Assert.isNotNull(project);
        Assert.isNotNull(newLocationParent);

        if (!newLocationParent.hasTrailingSeparator())
        {
            newLocationParent.addTrailingSeparator();
        }

        try
        {
            IResource[] members = project.members();

            monitor.beginTask("Relocating Files", members.length * 2);

            for (int i = 0; i < members.length; i++)
            {
                if (members[i].isLinked() && !members[i].getRawLocation().toFile().exists())
                {
                    // the linked file points to a file that does not exist.
                    String name = members[i].getName();
                    IPath newLocation = newLocationParent.append(name);
                    if (newLocation.toFile().exists())
                    {

                        members[i].delete(true, new SubProgressMonitor(monitor, 1));

                        getLinkedFile(project, newLocation.toOSString(), true);
                        monitor.worked(1);

                        Activator.logDebug("File found " + newLocation.toOSString());
                    } else
                    {
                        throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Error relocating file "
                                + name + ". The specified location " + newLocation.toOSString()
                                + " did not contain the file."));
                    }
                } else
                {
                    monitor.worked(2);
                }

            }

        } catch (CoreException e)
        {
            Activator.logError("Error relocating files in " + project.getName(), e);
        } finally
        {
            monitor.done();
        }

    }

    /**
     * Retrieves a a resource from the project, creates a link, if the file is not present 
     * convenience method for getLinkedFile(project, name, true)  
     */
    public static IFile getLinkedFile(IProject project, String name)
    {
        return getLinkedFile(project, name, true);
    }

    /**
     * Retrieves the path of the parent directory
     * @param path
     *            path of the module
     * @return path of the container
     */
    public static String getParentDirName(String path)
    {
        File f = new File(path);
        if (f != null)
        {
            return f.getParent();
        }
        return null;
    }

    /**
     * See {@link ResourceHelper#getParentDirName(String)}
     */
    public static String getParentDirName(IResource resource)
    {
        if (resource == null)
        {
            return null;
        }
        return getParentDirName(resource.getLocation().toOSString());
    }

    /**
     * Return the ModuleNode for the module named moduleName,
     * if one exists from the last time the spec was parsed.
     * Otherwise, it returns null.
     * 
     * There is no guarantee that this ModuleNode bears any
     * relation to the current state of the module's .tla
     * file.
     * @param moduleName
     * @return
     */
    public static ModuleNode getModuleNode(String moduleName)
    {
        SpecObj specObj = ToolboxHandle.getSpecObj();
        if (specObj == null)
        {
            return null;
        }
        return specObj.getExternalModuleTable().getModuleNode(UniqueString.uniqueStringOf(moduleName));
    }

    /**
     * Retrieves the name of the module (filename without extension)
     * 
     * @param moduleFilename
     *            filename of a module
     * @return module name, if the specified file exists of null, if the specified file does not exist
     */
    public static String getModuleName(String moduleFilename)
    {
        return getModuleNameChecked(moduleFilename, true);
    }

    /**
     * See {@link ResourceHelper#getModuleName(String)}
     */
    public static String getModuleName(IResource resource)
    {
        if (resource == null)
        {
            return null;
        }
        return getModuleName(resource.getLocation().toOSString());
    }

    /**
     * Retrieves the name of the module (filename without extension)
     * 
     * @param moduleFilename
     *            filename of a module
     * @param checkExistence
     *            if true, the method returns module name, iff the specified file exists or null, if the specified file
     *            does not exist, if false - only string manipulations are executed
     * @return module name
     */
    public static String getModuleNameChecked(String moduleFilename, boolean checkExistence)
    {
        File f = new File(moduleFilename);
        IPath path = new Path(f.getName()).removeFileExtension();
        // String modulename = f.getName().substring(0, f.getName().lastIndexOf("."));
        if (checkExistence)
        {
            return (f.exists()) ? path.toOSString() : null;
        }
        return path.toOSString();
    }

    /**
     * Creates a module name from a file name (currently, only adding .tla extension)
     * 
     * @param moduleName
     *            name of the module
     * @return resource name
     */
    public static String getModuleFileName(String moduleName)
    {
        if (moduleName == null || moduleName.equals(""))
        {
            return null;
        } else
        {
            return moduleName.concat(".").concat(TLA_EXTENSION);
        }
    }

    /**
     * Determines if the given member is a TLA+ module
     * @param resource
     * @return
     */
    public static boolean isModule(IResource resource)
    {
        return (resource != null && TLA_EXTENSION.equals(resource.getFileExtension()));

    }

    /**
     * Creates a simple content for a new TLA+ module
     *  
     * @param moduleFileName, name of the file 
     * @return the stream with content
     */
    public static StringBuffer getEmptyModuleContent(String moduleFilename)
    {
        StringBuffer buffer = new StringBuffer();
        String moduleName = ResourceHelper.getModuleNameChecked(moduleFilename, false);
        int numberOfDashes = Math.max(4, (Activator.getDefault().getPreferenceStore().getInt(
                EditorPreferencePage.EDITOR_RIGHT_MARGIN)
                - moduleName.length() - 9) / 2);
        String dashes = StringHelper.copyString("-", numberOfDashes);
        buffer.append(dashes).append(" MODULE ").append(moduleName).append(" ").append(dashes).append(
                StringHelper.copyString(StringHelper.newline, 3));
        return buffer;
    }

    /**
     * Returns the content for the end of the module
     * @return
     */
    public static StringBuffer getModuleClosingTag()
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append(
                StringHelper.copyString("=", Activator.getDefault().getPreferenceStore().getInt(
                        EditorPreferencePage.EDITOR_RIGHT_MARGIN))).append(modificationHistory).append(
                StringHelper.newline).append("\\* Created ").append(new Date()).append(" by ").append(
                System.getProperty("user.name")).append(StringHelper.newline);
        return buffer;
    }

    /**
     * Returns the content for the end of the module
     * @return
     */
    public static StringBuffer getConfigClosingTag()
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append("\\* Generated at ").append(new Date());
        return buffer;
    }

    /**
     * Creates a simple content for a new TLA+ module
     *  
     * @param moduleFileName, name of the file 
     * @return the stream with content
     */
    public static StringBuffer getExtendingModuleContent(String moduleFilename, String extendedModuleName)
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append("---- MODULE ").append(ResourceHelper.getModuleNameChecked(moduleFilename, false)).append(
                " ----\n").append("EXTENDS ").append(extendedModuleName).append(", TLC").append("\n\n");
        return buffer;
    }

    /**
     * Checks, whether the module is the root file of loaded spec
     * @param module the 
     * @return
     */
    public static boolean isRoot(IFile module)
    {
        Spec spec = Activator.getSpecManager().getSpecLoaded();
        if (spec == null)
        {
            return false;
        }
        return spec.getRootFile().equals(module);
    }

    /**
     * Constructs qualified name
     * @param localName
     * @return
     */
    public static QualifiedName getQName(String localName)
    {
        return new QualifiedName(Activator.PLUGIN_ID, localName);
    }

    /**
     * @param rootNamePath
     * @return
     */
    public static IWorkspaceRunnable createTLAModuleCreationOperation(IPath rootNamePath)
    {
        return new NewTLAModuleCreationOperation(rootNamePath);
    }

    /**
     * Writes contents stored in the string buffer to the file, replacing the content 
     * @param file
     * @param buffer
     * @param monitor
     * @throws CoreException
     */
    public static void replaceContent(IFile file, StringBuffer buffer, IProgressMonitor monitor) throws CoreException
    {
        ByteArrayInputStream stream = new ByteArrayInputStream(buffer.toString().getBytes());
        if (file.exists())
        {
            // System.out.println(buffer.toString());
            file.setContents(stream, IResource.FORCE, monitor);
        } else
        {
            throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Exected " + file.getName()
                    + " file has been removed externally"));
            // this will create a file in a wrong location
            // instead of /rootfile-dir/file.cfg it will create it under /specdir/file.cfg
            // file.create(stream, force, monitor);
        }
    }

    /**
     * Writes contents stored in the string buffer to the file, appending the content
     * @param file
     * @param buffer
     * @param monitor
     * @throws CoreException
     */
    public static void addContent(IFile file, StringBuffer buffer, IProgressMonitor monitor) throws CoreException
    {
        boolean force = true;
        ByteArrayInputStream stream = new ByteArrayInputStream(buffer.toString().getBytes());
        if (file.exists())
        {
            // System.out.println(buffer.toString());
            file.appendContents(stream, IResource.FORCE, monitor);
        } else
        {
            file.create(stream, force, monitor);
        }
    }

    /**
     * Retrieves a combined rule for modifying the resources
     * @param resources set of resources
     * @return a combined rule
     */
    public static ISchedulingRule getModifyRule(IResource[] resources)
    {
        if (resources == null)
        {
            return null;
        }
        ISchedulingRule combinedRule = null;
        IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        for (int i = 0; i < resources.length; i++)
        {
            // if one of the resources does not exist
            // something is screwed up
            if (resources[i] == null || !resources[i].exists())
            {
                return null;
            }
            ISchedulingRule rule = ruleFactory.modifyRule(resources[i]);
            combinedRule = MultiRule.combine(rule, combinedRule);
        }
        return combinedRule;
    }

    public static ISchedulingRule getCreateRule(IResource resource)
    {
        IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        return ruleFactory.createRule(resource);
    }

    /**
     * Retrieves a rule for modifying a resource
     * @param resource
     * @return
     */
    public static ISchedulingRule getModifyRule(IResource resource)
    {
        IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        ISchedulingRule rule = ruleFactory.modifyRule(resource);
        return rule;
    }

    /**
     * Retrieves a combined rule for deleting resource
     * @param resource
     * @return
     */
    public static ISchedulingRule getDeleteRule(IResource[] resources)
    {
        if (resources == null)
        {
            return null;
        }
        ISchedulingRule combinedRule = null;
        IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        for (int i = 0; i < resources.length; i++)
        {
            ISchedulingRule rule = ruleFactory.deleteRule(resources[i]);
            combinedRule = MultiRule.combine(rule, combinedRule);
        }
        return combinedRule;
    }

    /**
     * Retrieves a combined rule for creating resource
     * @param resource
     * @return
     */
    public static ISchedulingRule getCreateRule(IResource[] resources)
    {
        if (resources == null)
        {
            return null;
        }
        ISchedulingRule combinedRule = null;
        IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        for (int i = 0; i < resources.length; i++)
        {
            ISchedulingRule rule = ruleFactory.createRule(resources[i]);
            combinedRule = MultiRule.combine(rule, combinedRule);
        }
        return combinedRule;
    }

    /**
     * Renames and moves the project
     * @param project
     * @param specName
     */
    public static IProject projectRename(IProject project, String specName)
    {
        IProgressMonitor monitor = new NullProgressMonitor();
        try
        {
            IProjectDescription description = project.getDescription();

            // move the project location
            IPath path = description.getLocation().removeLastSegments(1).removeTrailingSeparator().append(
                    specName.concat(TOOLBOX_DIRECTORY_SUFFIX)).addTrailingSeparator();
            description.setLocation(path);
            description.setName(specName);

            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            project.copy(description, IResource.NONE | IResource.SHALLOW, monitor);
            project.delete(IResource.NONE, monitor);

            return ResourcesPlugin.getWorkspace().getRoot().getProject(specName);
        } catch (CoreException e)
        {
            Activator.logError("Error renaming a specification", e);
        }
        return null;
    }

    /**
     * Deletes the project
     * @param project
     */
    public static void deleteProject(IProject project)
    {
        IProgressMonitor monitor = new NullProgressMonitor();
        try
        {
            project.delete(true, monitor);
        } catch (CoreException e)
        {
            Activator.logError("Error deleting a specification", e);
        }
    }

    /**
     * If file has been parsed since it was last written, then return
     * its parseResult.  Else, return null.
     * @param file
     * @return
     */
    public static ParseResult getValidParseResult(IFile file)
    {

        ParseResult parseResult = ParseResultBroadcaster.getParseResultBroadcaster().getParseResult(file.getLocation());
        if ((parseResult == null))
        {
            return null;
        }

        long timeWhenParsed = parseResult.getParserCalled();
        long timeWhenWritten = file.getLocalTimeStamp();
        if ((timeWhenWritten == IResource.NULL_STAMP) || (timeWhenWritten > timeWhenParsed))
        {
            return null;
        }
        return parseResult;
    }

    /**
     * If parseResult's status is not equal to PARSED, returns null.  Else, it tries to find a TheoremNode in
     * module moduleName "at" the point textSelection.  More precisely, it is the TheoremNode whose location
     * is the first one on the line containing the offset of textSelection.  
     * 
     * The method assumes that document is the document for the module.
     * 
     * @param parseResult
     * @param moduleName
     * @param textSelection
     * @param document
     * @return
     */
    public static TheoremNode getTheoremNodeWithCaret(ParseResult parseResult, String moduleName,
            ITextSelection textSelection, IDocument document)
    {
        if ((parseResult == null) || (parseResult.getStatus() != IParseConstants.PARSED))
        {
            return null;
        }
        ModuleNode module = parseResult.getSpecObj().getExternalModuleTable().getModuleNode(
                UniqueString.uniqueStringOf(moduleName));
        if (module == null)
        {
            return null;
        }
        TheoremNode[] theorems = module.getTheorems();

        TheoremNode stepWithCaret = null;

        for (int i = 0; i < theorems.length; i++)
        {
            TheoremNode theoremNode = theorems[i];

            if (theoremNode.getLocation().source().equals(moduleName))
            {
                /*
                 * Found a theorem in the module.
                 * 
                 * See if it has a step containing the caret.
                 * 
                 * The caret is located at the end of the current
                 * selection if a range of text is selected (highlighted).
                 */
                TheoremNode step = UIHelper.getThmNodeStepWithCaret(theoremNode, textSelection.getOffset()
                /* + textSelection.getLength() */, document);

                if (step != null)
                {
                    // found the step with the caret
                    stepWithCaret = step;
                    break;
                }
            }
        }

        return stepWithCaret;

    }

    /**
     * If parseResult's status is not equal to PARSED, returns null.  Else, it tries to find a {@link LevelNode}
     * representing a proof step or a top level USE/HIDE node in module moduleName "at" the point textSelection. 
     * More precisely, the caret is at a LevelNode if the level node is the first proof step or USE/HIDE node
     * on the line containing the offset of textSelection.  
     * 
     * The method assumes that document is the document for the module.
     * 
     * @param parseResult
     * @param moduleName
     * @param textSelection
     * @param document
     * @return
     */
    public static LevelNode getPfStepOrUseHideFromMod(ParseResult parseResult, String moduleName,
            ITextSelection textSelection, IDocument document)
    {
        try
        {

            if ((parseResult == null) || (parseResult.getStatus() != IParseConstants.PARSED))
            {
                return null;
            }
            ModuleNode module = parseResult.getSpecObj().getExternalModuleTable().getModuleNode(
                    UniqueString.uniqueStringOf(moduleName));
            if (module == null)
            {
                return null;
            }

            LevelNode[] topLevelNodes = module.getTopLevel();

            for (int i = 0; i < topLevelNodes.length; i++)
            {

                if (topLevelNodes[i].getLocation().source().equals(moduleName))
                {
                    /*
                     * If the level node is a use or hide node search to see if the
                     * caret is at that node. If the node is a theorem node, see if
                     * the caret is at a node in the tree rooted at the theorem node.
                     */
                    LevelNode node = getLevelNodeFromTree(topLevelNodes[i], document.getLineOfOffset(textSelection
                            .getOffset()) + 1);
                    if (node != null)
                    {
                        return node;
                    }
                }

            }
        } catch (BadLocationException e)
        {
            Activator.logError("Error getting line number of caret.", e);
        }
        return null;

    }

    /**
     * Returns the {@link LevelNode} in the tree rooted at levelNode that is the first
     * on the line lineNum. Returns null if no such node found. Assumes that levelNode is one of
     * 
     * {@link UseOrHideNode}
     * {@link InstanceNode}
     * {@link TheoremNode}
     * {@link DefStepNode}
     * 
     * If that is not true, this method returns null.
     * 
     * This method was modified by LL on 11 June 2010 so that if the line is in a leaf proof,
     * then it returns the step that proof is proving.  More precisely, it returns that step
     * if lineNum is between the lines on which the leaf proof begins and ends.  In this example:
     * 
     *   line 10 : <2>1. X
     *   line 11 :   OBVIOUS <2>2. Y
     *   
     * if lineNum = 11 then the method returns the step <2>1.  This is weird, but it always does
     * the right thing if a step always begins a line, which it should.
     * 
     * @param levelNode
     * @param lineNum
     * @return
     */
    public static LevelNode getLevelNodeFromTree(LevelNode levelNode, int lineNum)
    {
        /*
         * Get the location of the step.    
         * 
         * For a TheoremNode, the step is from the beginning of the theorem
         * node to the end of the statement of the
         * theorem node. TheoremNode.getTheorem() returns the node
         * corresponding to the statement of the step (or theorem).
         * 
         * For other nodes the location is simply the begin line
         * to the end line of the node.
         * 
         * Return levelNode if the caret is on any of the lines
         * from of the location of the node.
         * 
         * If the caret is not on any of those lines and the node is a TheoremNode, then
         * recursively search for a substep containing the caret. Since the steps
         * are recursively searched in order, this will return the step
         * that is first on lineNum, if there is such a step.
         */
        int nodeBeginLine = levelNode.getLocation().beginLine();
        int nodeEndLine = 0;
        if (levelNode instanceof TheoremNode)
        {
            nodeEndLine = ((TheoremNode) levelNode).getTheorem().getLocation().endLine();
        } else if (levelNode instanceof UseOrHideNode || levelNode instanceof InstanceNode
                || levelNode instanceof DefStepNode)
        {
            nodeEndLine = levelNode.getLocation().endLine();
        } else
        {
            return null;
        }

        if (nodeBeginLine <= lineNum && nodeEndLine >= lineNum)
        {
            return levelNode;
        }

        if (levelNode instanceof TheoremNode)
        {
            /*
             * If the theorem has a proof and the lineNum is in it,
             * then if it is a non-leaf proof, recursively search for 
             * a substep.  Otherwise, it is a leaf proof containing
             * the lineNum and we return the theorem node.
             */
            TheoremNode theoremNode = (TheoremNode) levelNode;

            ProofNode proof = theoremNode.getProof();
            if (proof != null)
            {
                Location proofLoc = proof.getLocation();
                if (lineNum >= proofLoc.beginLine() && lineNum <= proofLoc.endLine())
                {
                    if (proof instanceof NonLeafProofNode)
                    {
                        NonLeafProofNode nonLeafProof = (NonLeafProofNode) proof;
                        LevelNode[] steps = nonLeafProof.getSteps();

                        for (int i = 0; i < steps.length; i++)
                        {
                            LevelNode node = getLevelNodeFromTree(steps[i], lineNum);
                            if (node != null)
                            {
                                return node;
                            }

                        }
                    }

                    else
                    {
                        return levelNode;
                    }
                }
            }

        }
        return null;
    }

    /**
     * Returns a document representing the document given
     * by module name in the current spec (name without tla extension).
     * Note that this document will not be synchronized with changes to
     * the file. It will simply be a snapshot of the file.
     * 
     * @param moduleName
     * @return
     */
    public static IDocument getDocFromName(String moduleName)
    {
        IFile module = (IFile) getResourceByModuleName(moduleName);
        return getDocFromFile(module);
    }

    /**
     * Returns a document representing the file. Note that
     * this document will not be synchronized with changes to
     * the file. It will simply be a snapshot of the file.
     * 
     * Returns null if unsuccessful.
     * 
     * @param module
     * @return
     */
    public static IDocument getDocFromFile(IFile file)
    {

        /*
         * We need a file document provider to create
         * the document. After the document is created
         * the file document provider must be disconnected
         * to avoid a memory leak.
         */
        FileDocumentProvider fdp = new FileDocumentProvider();
        FileEditorInput input = new FileEditorInput(file);
        try
        {
            fdp.connect(input);
            return fdp.getDocument(input);
        } catch (CoreException e)
        {
            Activator.logError("Error getting document for module " + file, e);
        } finally
        {
            fdp.disconnect(input);
        }
        return null;

    }

    /**
     * Sets the ToolboxDirSize property, which equals the number of kbytes of storage
     * used by the spec's .toolbox directory, where resource is the IProject object
     * for the spec.
     * 
     * @param resource
     */
    public static void setToolboxDirSize(IProject resource)
    {
        // set dirSize to the size of the .toolbox directory
        long dirSize = ResourceHelper.getSizeOfJavaFileResource(resource);

        // Set the size property of the Spec's property page spec to the Spec object for which
        IPreferenceStore preferenceStore = PreferenceStoreHelper.getProjectPreferenceStore(resource);

        preferenceStore.setValue(IPreferenceConstants.P_PROJECT_TOOLBOX_DIR_SIZE, String.valueOf(dirSize / 1000));
    }

    /**
     * Called to find the number of bytes contained within an IResource
     * that represents a Java File object (a file or directory).  Returns
     * 0 if resource or its File are null.
     * 
     * @param resource
     * @return
     */
    public static long getSizeOfJavaFileResource(IResource resource)
    {
        // Set file to the Java File represented by the resource.
        if (resource == null)
        {
            return 0;
        }
        // Check for resource.getLocation() = null added 30 May 2010
        // by LL.
        IPath ipath = resource.getLocation();
        if (ipath == null)
        {
            return 0;
        }
        File file = ipath.toFile();

        if (file == null)
        {
            return 0;
        }
        return getDirSize(file);
    }

    /**
     * If dir is a directory, return the size of all
     * @param dir
     * @return
     */
    private static long getDirSize(File dir)
    {
        long size = 0;
        if (dir.isFile())
        {
            size = dir.length();
        } else
        {
            File[] subFiles = dir.listFiles();
            size += dir.length();
            for (int i = 0; i < subFiles.length; i++)
            {
                File file = subFiles[i];

                if (file.isFile())
                {
                    size += file.length();
                } else
                {
                    size += getDirSize(file);
                }
            }
        }
        return size;
    }
}
