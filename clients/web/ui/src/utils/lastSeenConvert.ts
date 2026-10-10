export function lastSeenConvert(timestamp: number) {
    const diff = Date.now() - timestamp * 1000 //timestamp của API là seconds
    const mins = Math.floor(diff / 60 / 1000)
    const hours = Math.floor(diff / 60 / 60 / 1000)
    const days = Math.floor(hours/ 24)

    if (mins < 1) {
        return `Active just now`
    }
    if (mins < 60) {
        return `Active ${mins} min${mins === 1 ? '' : 's'} ago`
    }
    if (hours < 48) {
        return `Active ${hours} hour${hours === 1 ? '' : 's'} ago`
    }
    else {
        return `Active ${days} day${days === 1 ? '' : 's'} ago`
    }
}