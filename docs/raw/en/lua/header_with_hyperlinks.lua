pandoc.utils = require 'pandoc.utils'

-- Adds links to headings with IDs.
function Header (h)
    if h.level < 4 then
        local anchor_link = pandoc.Link(
            h.content,
            '#toc:' .. h.identifier
        )
        h.content = pandoc.Inlines {anchor_link}
    end
    if h.identifier ~= '' then
        -- An empty link to this header
        local anchor_link = pandoc.Link(
            {},                  -- content
            '#' .. h.identifier, -- href
            '',                  -- title
            {class = 'anchor', ['aria-hidden'] = 'true'} -- attributes
        )
        h.content:insert(anchor_link)
    end
    return h
end

if FORMAT:match 'html' then
    return {
        {Header = Header}
    }
end
