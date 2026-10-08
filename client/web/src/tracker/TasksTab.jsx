import { ACTIVITY_TYPE } from './habitOptions'
import ActivityListTab from './ActivityListTab'

export default function TasksTab() {
  return (
    <ActivityListTab
      type={ACTIVITY_TYPE.TASK}
      idPrefix="tasks"
      addLabel="Добавить задачу"
      emptyMessage="На выбранную дату задач нет."
      modalTitle="Новая задача"
      entityLabel="задачи"
      renameTitle="Переименовать задачу"
    />
  )
}
