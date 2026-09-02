// 集計ロジック。ブラウザでそのまま動くよう依存を持たない。
//
// **週の境界の規則は server/src/aggregate.ts の SQL および
// android/.../domain/Aggregate.kt と完全に一致させること。**
// ズレると同じ週なのに違う数字が出る。

/**
 * その日が属する週の月曜日を返す。
 *
 * `%Y-%W` 相当でグルーピングしてはいけない（年をまたぐ週が分断される）。
 * @param {string} dateText YYYY-MM-DD
 * @returns {string} YYYY-MM-DD
 */
export function weekStart(dateText) {
  const d = new Date(dateText + "T00:00:00Z");
  // getUTCDay(): 0=日曜。月曜起点にするため (day + 6) % 7 日ぶん戻す
  const back = (d.getUTCDay() + 6) % 7;
  d.setUTCDate(d.getUTCDate() - back);
  return d.toISOString().slice(0, 10);
}

export function daysInMonth(key) {
  const [y, m] = key.split("-").map(Number);
  return new Date(Date.UTC(y, m, 0)).getUTCDate();
}

export function isLeapYear(y) {
  return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
}

export function daysInPeriod(key, period) {
  switch (period) {
    case "day": return 1;
    case "week": return 7;
    case "month": return daysInMonth(key);
    case "year": return isLeapYear(Number(key)) ? 366 : 365;
    default: return 1;
  }
}

/**
 * 日次の記録を期間ごとにまとめる。
 *
 * **平均の分母は記録がある日だけ。** 未計測の日を0歩として平均を下げない。
 * 0歩と記録された日は分母に含める。
 *
 * @param {Record<string, number>} stepsByDate 日付 -> 歩数（記録がある日だけ）
 * @param {"day"|"week"|"month"|"year"} period
 */
export function aggregate(stepsByDate, period) {
  const groups = new Map();
  for (const [dateText, steps] of Object.entries(stepsByDate)) {
    let key;
    switch (period) {
      case "day": key = dateText; break;
      case "week": key = weekStart(dateText); break;
      case "month": key = dateText.slice(0, 7); break;
      case "year": key = dateText.slice(0, 4); break;
    }
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(steps);
  }

  return [...groups.entries()]
    .map(([key, values]) => {
      const total = values.reduce((a, b) => a + b, 0);
      return {
        key,
        total,
        average: total / values.length,
        daysRecorded: values.length,
        daysInPeriod: daysInPeriod(key, period),
      };
    })
    .sort((a, b) => (a.key < b.key ? 1 : -1));
}

/** 目標に対する達成率。リング表示のため 1 で頭打ちにする。 */
export function achievedRatio(steps, goal) {
  if (goal <= 0) return 0;
  return Math.min(1, Math.max(0, steps / goal));
}

export function isAchieved(steps, goal) {
  return steps >= goal;
}

/**
 * 連続達成日数。
 *
 * **未計測の日は切らない**（端末を持たなかっただけかもしれない）。
 * **0歩と記録された日は切る**（実際に歩かなかったと確定している）。
 * **当日が未達でも切らない**（まだ挽回できる）。
 */
export function currentStreak(stepsByDate, todayText, goal) {
  let streak = 0;
  let date = todayText;

  const todaySteps = stepsByDate[todayText];
  if (todaySteps === undefined || !isAchieved(todaySteps, goal)) {
    date = shiftDate(todayText, -1);
  }

  const oldest = Object.keys(stepsByDate).sort()[0];
  if (oldest === undefined) return 0;

  while (date >= oldest) {
    const steps = stepsByDate[date];
    if (steps !== undefined) {
      if (isAchieved(steps, goal)) streak++;
      else return streak;      // 記録があって未達 → ここで途切れる
    }
    date = shiftDate(date, -1);
  }
  return streak;
}

/** これまでの最長の連続達成日数。 */
export function longestStreak(stepsByDate, goal) {
  const achieved = Object.keys(stepsByDate)
    .filter((d) => isAchieved(stepsByDate[d], goal))
    .sort();
  if (achieved.length === 0) return 0;

  let best = 1;
  let run = 1;
  for (let i = 1; i < achieved.length; i++) {
    // 間の日がすべて未計測なら連続とみなす
    let gapAllUnmeasured = true;
    for (let d = shiftDate(achieved[i - 1], 1); d < achieved[i]; d = shiftDate(d, 1)) {
      if (stepsByDate[d] !== undefined) { gapAllUnmeasured = false; break; }
    }
    run = gapAllUnmeasured ? run + 1 : 1;
    best = Math.max(best, run);
  }
  return best;
}

export function shiftDate(dateText, days) {
  const d = new Date(dateText + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * 直前の期間との平均の差。
 * **期間の途中では同じ経過日数までで比べる**（月初の不公平を避ける）。
 */
export function compareAverages(current, previous, elapsedDays) {
  const cur = Object.values(current);
  if (cur.length === 0) return null;

  const prev = Object.keys(previous).sort().slice(0, elapsedDays).map((k) => previous[k]);
  if (prev.length === 0) return null;

  const avg = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
  return avg(cur) - avg(prev);
}
