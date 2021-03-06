package de.objectcode.time4u.client.ui.provider;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import de.objectcode.time4u.client.store.api.IProjectRepository;
import de.objectcode.time4u.client.ui.UIPlugin;
import de.objectcode.time4u.server.api.data.ProjectSummary;
import de.objectcode.time4u.server.api.filter.ProjectFilter;

/**
 * Project content provider.
 * 
 * @author junglas
 */
public class ProjectContentProvider implements IStructuredContentProvider, ITreeContentProvider
{
  private final IProjectRepository m_projectRepository;
  private final boolean m_onlyActive;
  private final boolean m_includeRoot;

  /** Display string for the non-existent root project. */
  public static final String ROOT = "[Root]";

  /**
   * Create a new ProjectContentProvider.
   * 
   * @param projectRepository
   *          The project repository
   * @param onlyActive
   *          Show only active projects
   */
  public ProjectContentProvider(final IProjectRepository projectRepository, final boolean onlyActive)
  {
    m_projectRepository = projectRepository;
    m_onlyActive = onlyActive;
    m_includeRoot = false;
  }

  /**
   * Create a new ProjectContentProvider.
   * 
   * @param projectRepository
   *          The project repository
   * @param onlyActive
   *          Show only active projects
   * @param includeRoot
   *          Also display the root project as allowed selection
   */
  public ProjectContentProvider(final IProjectRepository projectRepository, final boolean onlyActive,
      final boolean includeRoot)
  {
    m_projectRepository = projectRepository;
    m_onlyActive = onlyActive;
    m_includeRoot = includeRoot;
  }

  /**
   * {@inheritDoc}
   */
  public Object[] getChildren(final Object parentElement)
  {
    try {
      final ProjectFilter filter = new ProjectFilter();

      filter.setDeleted(false);
      if (m_onlyActive) {
        filter.setActive(true);
      }

      if (parentElement instanceof ProjectSummary) {
        return m_projectRepository.getProjectSumaries(
            ProjectFilter.filterChildProjects(((ProjectSummary) parentElement).getId(), m_onlyActive)).toArray();
      } else {
        return m_projectRepository.getProjectSumaries(ProjectFilter.filterRootProjects(m_onlyActive)).toArray();
      }
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }

    return new Object[0];
  }

  /**
   * {@inheritDoc}
   */
  public Object getParent(final Object element)
  {
    try {
      if (element instanceof ProjectSummary) {
        if (((ProjectSummary) element).getParentId() != null) {
          return m_projectRepository.getProject(((ProjectSummary) element).getParentId());
        } else if (m_includeRoot) {
          return ROOT;
        }
      }
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }

    return null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasChildren(final Object parentElement)
  {
    return getChildren(parentElement).length > 0;
  }

  /**
   * {@inheritDoc}
   */
  public Object[] getElements(final Object inputElement)
  {
    if (m_includeRoot) {
      return new Object[] {
        ROOT
      };
    }
    return getChildren(inputElement);
  }

  /**
   * {@inheritDoc}
   */
  public void dispose()
  {
  }

  /**
   * {@inheritDoc}
   */
  public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput)
  {
  }
}
