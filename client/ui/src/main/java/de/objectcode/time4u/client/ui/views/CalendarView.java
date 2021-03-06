package de.objectcode.time4u.client.ui.views;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.ViewPart;
import org.vafada.swtcalendar.SWTCalendar;
import org.vafada.swtcalendar.SWTCalendarEvent;
import org.vafada.swtcalendar.SWTCalendarListener;
import org.vafada.swtcalendar.SWTDayChooser.DayControl;
import de.objectcode.time4u.client.store.api.IWorkItemRepository;
import de.objectcode.time4u.client.store.api.RepositoryException;
import de.objectcode.time4u.client.store.api.RepositoryFactory;
import de.objectcode.time4u.client.store.api.event.IRepositoryListener;
import de.objectcode.time4u.client.store.api.event.RepositoryEvent;
import de.objectcode.time4u.client.store.api.event.RepositoryEventType;
import de.objectcode.time4u.client.ui.UIPlugin;
import de.objectcode.time4u.client.ui.dnd.TaskTransfer;
import de.objectcode.time4u.client.ui.dnd.WorkItemTransfer;
import de.objectcode.time4u.client.ui.provider.DayFontColorProvider;
import de.objectcode.time4u.client.ui.util.CompoundSelectionEntityType;
import de.objectcode.time4u.client.ui.util.CompoundSelectionProvider;
import de.objectcode.time4u.client.ui.util.SelectionServiceAdapter;
import de.objectcode.time4u.server.api.data.CalendarDay;
import de.objectcode.time4u.server.api.data.DayInfo;
import de.objectcode.time4u.server.api.data.DayTag;
import de.objectcode.time4u.server.api.data.WorkItem;

public class CalendarView extends ViewPart implements SWTCalendarListener, IRepositoryListener
{
  public static final String ID = "de.objectcode.time4u.client.ui.view.calendarView";

  SWTCalendar m_calendar;

  int m_currentMonth;
  int m_currentYear;

  int m_refreshCounter = 0;

  private CompoundSelectionProvider m_selectionProvider;
  private final Set<TimePolicyAction> m_timePolicyActions;

  Font m_boldFont;
  Font m_italicFont;

  public CalendarView()
  {
    m_timePolicyActions = new HashSet<CalendarView.TimePolicyAction>();
  }
  /**
   * This is a callback that will allow us to create the viewer and initialize it.
   */
  @Override @SuppressWarnings("unchecked")
  public void createPartControl(final Composite parent)
  {
    m_selectionProvider = new CompoundSelectionProvider();
    getSite().setSelectionProvider(m_selectionProvider);
    getSite().getPage().addSelectionListener(m_selectionProvider);

    m_calendar = new SWTCalendar(parent, SWTCalendar.SHOW_WEEK_NUMBERS);
    m_calendar.addSWTCalendarListener(this);
    m_calendar.addDropSupport(DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT, new Transfer[] {
        WorkItemTransfer.getInstance(), TaskTransfer.getInstance()
    }, new DropTargetAdapter() {
      @Override
      public void drop(final DropTargetEvent event)
      {
        if (event.data == null) {
          return;
        }

        if (event.data instanceof List) {
          doDropWorkItem((List<WorkItem>) event.data, ((DayControl) ((DropTarget) event.widget).getControl()).getDate(),
              (event.detail & DND.DROP_COPY) != 0);
        } else if (event.data instanceof TaskTransfer.ProjectTask) {
          doDropTask((TaskTransfer.ProjectTask) event.data, ((DayControl) ((DropTarget) event.widget).getControl())
              .getDate());
        }
      }

    });

    final CalendarDay selection = ((List<CalendarDay>)m_selectionProvider
        .getSelection(CompoundSelectionEntityType.CALENDARDAY)).get(0);
    Assert.isNotNull(selection);

    m_currentMonth = selection.getMonth();
    m_currentYear = selection.getYear();
    m_calendar.setCalendar(selection.getCalendar());

    final Font originalFont = m_calendar.getFont();
    final FontData boldFontData[] = originalFont.getFontData();
    final FontData italicFontData[] = originalFont.getFontData();

    // Adding the bold attribute
    for (int i = 0; i < boldFontData.length; i++) {
      boldFontData[i].setStyle(boldFontData[i].getStyle() | SWT.BOLD);
    }
    for (int i = 0; i < italicFontData.length; i++) {
      italicFontData[i].setStyle(italicFontData[i].getStyle() | SWT.ITALIC);
    }

    m_boldFont = new Font(m_calendar.getDisplay(), boldFontData);
    m_italicFont = new Font(m_calendar.getDisplay(), italicFontData);

    try {
      final IWorkItemRepository workItemRepository = RepositoryFactory.getRepository().getWorkItemRepository();

      final DayFontColorProvider provider = new DayFontColorProvider(m_calendar.getBackground(), m_calendar
          .getForeground(), m_calendar.getFont(), m_boldFont, m_italicFont, workItemRepository, m_currentYear,
          m_currentMonth);

      m_calendar.setColorProvider(provider);
      m_calendar.setFontProvider(provider);
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }

    final MenuManager menuMgr = new MenuManager();

    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(createMenuListener(menuMgr));

    final Menu menu = menuMgr.createContextMenu(m_calendar);

    m_calendar.setMenu(menu);

    getSite().registerContextMenu(menuMgr, new SelectionServiceAdapter(getSite().getPage()));

    RepositoryFactory.getRepository().addRepositoryListener(RepositoryEventType.DAYINFO, this);
    RepositoryFactory.getRepository().addRepositoryListener(RepositoryEventType.TIMEPOLICY, this);
  }

  public IMenuListener createMenuListener(final MenuManager menuMgr){

    IMenuListener menuListener = new IMenuListener() {
      public void menuAboutToShow(final IMenuManager manager )
      {
        try {
          final CalendarDay selection = ((List<CalendarDay>)m_selectionProvider
              .getSelection(CompoundSelectionEntityType.CALENDARDAY)).get(0);
          final DayInfo dayInfo = RepositoryFactory.getRepository().getWorkItemRepository().getDayInfo(selection);
          final List<DayTag> dayTags = RepositoryFactory.getRepository().getWorkItemRepository().getDayTags();

          boolean hasNoTags = dayInfo != null?!dayInfo.isHasTags():true;
          TimePolicyAction markFreeDayAction = new TimePolicyAction("action.timepolicy.markfree.label" 
              ,"de.objectcode.time4u.client.ui.timepolicy.markfree" , dayInfo != null && dayInfo.getRegularTime() == 0, hasNoTags);
          TimePolicyAction markRegularDayAction = new TimePolicyAction("action.timepolicy.markregular.label" 
              ,"de.objectcode.time4u.client.ui.timepolicy.markregular" , 
              dayInfo != null && (dayInfo.getRegularTime() < 0 || dayInfo.getRegularTime() > 0), hasNoTags);

          menuMgr.add(markRegularDayAction);
          menuMgr.add(markFreeDayAction);

          m_timePolicyActions.clear();
          m_timePolicyActions.add(markFreeDayAction);
          m_timePolicyActions.add(markRegularDayAction);

          menuMgr.add(new GroupMarker("calendarGroup"));
          menuMgr.add(new Separator());
          menuMgr.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
          Assert.isNotNull(selection);

          final Set<String> currentTags = dayInfo != null ? dayInfo.getTags() : new HashSet<String>();
          final int regularTime = dayInfo != null ? dayInfo.getRegularTime() : -1;

          if (!dayTags.isEmpty()) {
            menuMgr.add(new Separator());
            for (final DayTag dayTag : dayTags) {
              SetDayTagAction action = new SetDayTagAction(selection, regularTime, currentTags, dayTag);
              menuMgr.add(action);
            }
          }
        } catch (final Exception e) {
          UIPlugin.getDefault().log(e);
        }
      }
    };
    return menuListener;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setFocus()
  {
    m_calendar.setFocus();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void dispose()
  {
    RepositoryFactory.getRepository().removeRepositoryListener(RepositoryEventType.DAYINFO, this);
    RepositoryFactory.getRepository().removeRepositoryListener(RepositoryEventType.TIMEPOLICY, this);

    super.dispose();
  }

  public void refresh()
  {
    try {
      final IWorkItemRepository workItemRepository = RepositoryFactory.getRepository().getWorkItemRepository();
      final DayFontColorProvider provider = new DayFontColorProvider(m_calendar.getBackground(), m_calendar
          .getForeground(), m_calendar.getFont(), m_boldFont, m_italicFont, workItemRepository, m_currentYear,
          m_currentMonth);

      m_calendar.setColorProvider(provider);
      m_calendar.setFontProvider(provider);
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void dateChanged(final SWTCalendarEvent event)
  {
    final Calendar calendar = event.getCalendar();

    if (calendar.get(Calendar.MONTH) + 1 != m_currentMonth || calendar.get(Calendar.YEAR) != m_currentYear) {
      m_currentMonth = calendar.get(Calendar.MONTH) + 1;
      m_currentYear = calendar.get(Calendar.YEAR);

      refresh();
    }

    m_selectionProvider.changeSelection(CompoundSelectionEntityType.CALENDARDAY, new CalendarDay(calendar));
  }

  /**
   * {@inheritDoc}
   */
  public void handleRepositoryEvent(final RepositoryEvent event)
  {
    // It's not necessary to queue more than 2 refreshes

    switch (event.getEventType()) {
      case DAYINFO:
      case TIMEPOLICY:
        synchronized (this) {
          if (m_refreshCounter >= 2) {
            return;
          }

          m_refreshCounter++;
        }
        m_calendar.getDisplay().asyncExec(new Runnable() {
          public void run()
          {
            try {
              refresh();
            } finally {
              synchronized (CalendarView.this) {
                m_refreshCounter--;
              }
            }
          }
        });
        break;
    }
  }

  private void doDropWorkItem(final List<WorkItem> workItems, final Date date, final boolean copy)
  {
    try {
      for(WorkItem workItem : workItems){
        if (!copy) {
          RepositoryFactory.getRepository().getWorkItemRepository().deleteWorkItem(workItem, true);
        }

        workItem.setId(null);
        workItem.setDay(new CalendarDay(date));

        RepositoryFactory.getRepository().getWorkItemRepository().storeWorkItem(workItem, true);
      }
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void doDropTask(final TaskTransfer.ProjectTask projectTask, final Date date)
  {
    try {
      int maxTime = 0;

      final DayInfo dayInfo = RepositoryFactory.getRepository().getWorkItemRepository().getDayInfo(
          new CalendarDay(date));

      if (dayInfo != null && dayInfo.getWorkItems() != null) {
        for (final WorkItem workItem : dayInfo.getWorkItems()) {
          if (workItem.getBegin() > maxTime) {
            maxTime = workItem.getBegin();
          }

          if (workItem.getEnd() > maxTime) {
            maxTime = workItem.getEnd();
          }
        }
      } else {
        final Calendar calendar = Calendar.getInstance();
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);

        maxTime = hour * 3600 + minute * 60;
      }

      final WorkItem workItem = new WorkItem();
      workItem.setProjectId(projectTask.getProject().getId());
      workItem.setTaskId(projectTask.getTask().getId());
      workItem.setBegin(maxTime);
      workItem.setEnd(maxTime);
      workItem.setDay(new CalendarDay(date));
      workItem.setComment("");

      RepositoryFactory.getRepository().getWorkItemRepository().storeWorkItem(workItem, true);
    } catch (final Exception e) {
      UIPlugin.getDefault().log(e);
    }
  }

  class TimePolicyAction extends Action{
    boolean m_checked;

    TimePolicyAction(String textId, String actionId, boolean checked, boolean enabled )
    {
      super(null, Action.AS_CHECK_BOX);

      String text = UIPlugin.getDefault().getString(textId);
      setId(actionId);
      setText(text);
      setToolTipText(text);
      setChecked(checked);
      setEnabled(enabled);
      m_checked = checked;
    }

    @Override
    public void run()
    {
      deSelectAllActions();
      final CalendarDay selectedDay = ((List<CalendarDay>)m_selectionProvider
          .getSelection(CompoundSelectionEntityType.CALENDARDAY)).get(0);
      final String id = this.getId();
      if ("de.objectcode.time4u.client.ui.timepolicy.markregular".equals(id)) {
        if (selectedDay != null) {
          try {
            RepositoryFactory.getRepository().getWorkItemRepository().setRegularTime(selectedDay, selectedDay, -1, null);
          } catch (final Exception e) {
            UIPlugin.getDefault().log(e);
          }
        }
      } else if ("de.objectcode.time4u.client.ui.timepolicy.markfree".equals(id)) {
        if (selectedDay != null) {
          try {
            RepositoryFactory.getRepository().getWorkItemRepository().setRegularTime(selectedDay, selectedDay, 0, null);
          } catch (final Exception e) {
            UIPlugin.getDefault().log(e);
          }
        }
      }
      m_checked = !m_checked;
      setChecked(m_checked);
    }

    private void deSelectAllActions()
    {
      for (TimePolicyAction timePolicyAction : m_timePolicyActions) {
        timePolicyAction.setChecked(false);
      }

    }
  }

  class SetDayTagAction extends Action
  {
    CalendarDay m_currentDay;
    Set<String> m_currentTags;
    DayTag m_dayTag;
    boolean m_checked;

    public SetDayTagAction(final CalendarDay currentDay, final int regularTime, final Set<String> currentTags,
        final DayTag dayTag)
    {
      super(dayTag.getName(), Action.AS_RADIO_BUTTON);

      setText(dayTag.getLabel());
      setToolTipText(dayTag.getDescription());

      m_currentDay = currentDay;
      m_currentTags = currentTags;
      m_dayTag = dayTag;
      m_checked = m_currentTags.contains(m_dayTag.getName());

      setChecked(m_checked);

    }

    @Override
    public void run()
    {
      m_currentTags.clear();
      if(this.isChecked()){
        m_currentTags.add(m_dayTag.getName());
      }

      try {
        Integer regularTime = getRegularTime();
        RepositoryFactory.getRepository().getWorkItemRepository().setRegularTime(m_currentDay, m_currentDay,
            regularTime, m_currentTags);
      } catch (final Exception e) {
        UIPlugin.getDefault().log(e);
      }
    }

    private Integer getRegularTime() throws RepositoryException
    {
      if(!m_currentTags.isEmpty()){
        return m_dayTag.getRegularTime();
      }

      Integer regularTime = RepositoryFactory.getRepository().getWorkItemRepository().getRegularTimeFromTimePolicy(m_currentDay);

      return regularTime;
    }

  }
  
}
