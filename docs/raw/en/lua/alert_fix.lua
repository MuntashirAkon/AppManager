function AlertFix(el)
    if el.classes:includes 'amalert--tip' then
        el.classes = {'amalert', 'tip'}
        return el
    end
    if el.classes:includes 'amalert--warning' then
        el.classes = {'amalert', 'warning'}
        return el
    end
    if el.classes:includes 'amalert--danger' then
        el.classes = {'amalert', 'danger'}
        return el
    end
    return nil
end

if FORMAT:match 'html' then
    return {
        {Div = AlertFix}
    }
end
