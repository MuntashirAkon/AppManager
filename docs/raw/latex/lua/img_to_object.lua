function SvgImage (el)
    if el.src:match '.svg$' then
        data = 'data="' .. el.src .. '" type="image/svg+xml"'
        id = el.identifier and ' id="' .. el.identifier .. '"' or nil
        classes = el.classes and ' class="' .. table.concat(el.classes, ' ') .. '"' or nil
        -- TODO
        -- figcaption = el.caption and '<figcaption aria-hidden="true">' .. table.concat(el.caption, ' ') .. '</figcaption>' or nil
        attr = ""
        for name, val in pairs(el.attributes) do
            attr = attr .. ' ' .. name .. '="' .. val .. '"'
        end
        return pandoc.RawInline('html', '<figure><object ' .. data .. id .. classes .. attr .. '></object></figure>')
    end
    return nil
end

if FORMAT:match 'html' then
    return {
        {Image = SvgImage}
    }
end
