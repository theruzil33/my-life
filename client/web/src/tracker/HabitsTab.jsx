import { ACTIVITY_TYPE } from './habitOptions'
import ActivityListTab from './ActivityListTab'

export default function HabitsTab() {
  return (
    <ActivityListTab
      type={ACTIVITY_TYPE.HABIT}
      idPrefix="habits"
      addLabel="Добавить привычку"
      emptyMessage="На выбранную дату привычек нет."
      modalTitle="Новая привычка"
      entityLabel="привычки"
      renameTitle="Переименовать привычку"
    />
  )
}
